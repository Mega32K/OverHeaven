package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.opengl.GL33C.*;
import com.mega.overheaven.foliagecompat.PatchLog;
import com.mega.overheaven.foliagecompat.blackhole.BlackHole;
import com.mojang.blaze3d.pipeline.RenderTarget;
import java.util.Arrays;
import java.util.Comparator;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class BlackHoleRenderer implements AutoCloseable {
    public static final BlackHoleRenderer INSTANCE = new BlackHoleRenderer(false);
    private final com.mega.overheaven.foliagecompat.finale.FinaleWaves.State waveBounds=new com.mega.overheaven.foliagecompat.finale.FinaleWaves.State();
    private static final String[] MATERIAL = {"radius","inner","outer","thickness","brightness","rotation","infall","lensing"};
    private static final String[] UNIFORMS = {"Radius","Inner","Outer","Thickness","Brightness","Rotation","Infall","Lensing"};
    private static final float[] CLEAR_LIGHT = {0,0,0,1}, CLEAR_META = {1000000,1000000,0,-1};
    private static final float[] UNIT_TRANSMISSION = {1,1,1,1}, FAR_DEPTH = {1,0,0,0};
    private static final Comparator<Visible> DISTANCE = Comparator.comparingDouble((Visible entry) -> entry.distance).reversed();
    private final Matrix4f viewProjection = new Matrix4f(), inverse = new Matrix4f();
    private final Vector4f opticalEye = new Vector4f();
    private final BlackHoleScreenBounds screenBounds = new BlackHoleScreenBounds();
    private final GlTarget opaque = new GlTarget(GL_RGBA16F, GL_R32F), transparent = new GlTarget(GL_RGBA16F, GL_R32F);
    private final GlTarget scene = new GlTarget(GL_RGBA16F), solution = new GlTarget(GL_RGBA16F,GL_RGBA16F,GL_RGBA32F,GL_RGBA32F);
    private final GlBloom bloom = new GlBloom();
    private final GlSingularityGlare glare = new GlSingularityGlare();
    private final GlCausalBackground opening=new GlCausalBackground();
    private final java.util.function.Consumer<GlProgram> openingUniforms=this::openingUniforms;
    private boolean openingActive,openingApplied;
    private final GlTarget foreground = new GlTarget(GL_RGBA16F);
    private final Visible[] visible = new Visible[256];
    private final boolean cosmic;
    private float partial;
    private GlDiskTexture diskTexture;
    private float pixelAngle;
    private GlProgram copy, integrate, composite, blur, foregroundComposite;
    private int vertexArray, outputFramebuffer, count, width, height;
    private boolean captured, surfaceCaptured, alphaBorrowed, failed;
    private Object level;
    private double cameraX, cameraY, cameraZ, time;
    private String path = "vanilla";
    private final GpuDiagnostics timing = new GpuDiagnostics();
    BlackHoleRenderer(boolean cosmic) { this.cosmic=cosmic; for (int index=0; index<visible.length; index++) visible[index]=new Visible(); }

    public void begin(Matrix4f modelView, Matrix4f projection, Camera camera, float partial) {
        if (OptionalShaders.shadow()) return;
        restoreAlpha();
        if (level != Minecraft.getInstance().level) { close(); level=Minecraft.getInstance().level; }
        captured=false; surfaceCaptured=false; count=0; openingActive=false; openingApplied=false;
        if (failed || level==null || BlackHoleClient.records().isEmpty() && !FinaleClient.active() || FinaleClient.skipWorld(partial)) return;
        if(!org.lwjgl.opengl.GL.getCapabilities().OpenGL33) { fail(new IllegalStateException("Black holes require OpenGL 3.3")); return; }
        var position=camera.getPosition(); cameraX=position.x; cameraY=position.y; cameraZ=position.z;
        matrices(modelView,projection);
        if (!Float.isFinite(opticalEye.w) || Math.abs(opticalEye.w)<1.0e-8) return;
        var target=Minecraft.getInstance().getMainRenderTarget(); width=target.width; height=target.height;
        pixelAngle=2.0f/(height*Math.max(0.001f,Math.abs(projection.m11())));
        time=BlackHoleClient.time(partial); path=OptionalShaders.active()?"oculus/complementary-r5.9.3":"vanilla";
        this.partial=partial;
        openingActive=CausalUniforms.sceneActive(partial);
        collectVisible();
        timing.frame(BlackHoleClient.diagnostics,active(),path,width,height);
    }
    private void collectVisible() {
        BlackHole source=FinaleClient.active()?FinaleClient.state().hole():null;
        if(source!=null) addVisible(source);
        for (BlackHole hole:BlackHoleClient.records()) {
            if(source!=null && source.id().equals(hole.id())) continue;
            addVisible(hole);
        }
        Arrays.sort(visible,0,count,DISTANCE);
    }
    private void addVisible(BlackHole hole) {
        if(count>=visible.length) return;
        Visible entry=visible[count]; entry.hole=hole;
        double relativeX=hole.x()-cameraX, relativeY=hole.y()-cameraY, relativeZ=hole.z()-cameraZ;
        entry.distance=relativeX*relativeX+relativeY*relativeY+relativeZ*relativeZ;
        double extent=hole.value("outer")*1.35+hole.value("thickness")*2.0;
        if(FinaleClient.source(hole.id())) {
            com.mega.overheaven.foliagecompat.finale.FinaleWaves.evaluate(FinaleClient.state().timeline(),FinaleClient.ticks(partial),hole.value("radius"),hole.value("outer"),hole.value("thickness"),waveBounds);
            extent=Math.max(extent,Math.max(Math.max(32.0,hole.value("radius")*1.5),waveBounds.support));
        }
        if(entry.distance>Math.pow(2048+extent,2)) return;
        if(project(entry,relativeX,relativeY,relativeZ,extent)) count++;
    }
    void renderCosmos(GlTarget destination,Matrix4f projectionView,Vector4f eye,double originX,double originY,double originZ,float angle,float partial) {
        if(!cosmic) throw new IllegalStateException("Cosmic input requires an independent renderer");
        if(failed) return;
        this.partial=partial; cameraX=originX; cameraY=originY; cameraZ=originZ;
        viewProjection.set(projectionView); inverse.set(viewProjection).invert(); opticalEye.set(eye);
        width=destination.width; height=destination.height; pixelAngle=angle;
        time=BlackHoleClient.time(partial); count=0; path="finale/cosmos";
        collectVisible();
        timing.frame(BlackHoleClient.diagnostics,count>0,path,width,height);
        if(count==0) return;
        try(var state=GlState.capture()) {
            initialize(width,height); GlState.fullscreen();
            transparent.bind(); glClearBufferfv(GL_COLOR,0,UNIT_TRANSMISSION); glClearBufferfv(GL_COLOR,1,FAR_DEPTH);
            opaque.bind(); copy.use(); copy.texture("Source",0,destination.textures[0]); copy.texture("Depth",1,transparent.textures[1]); copy.integer("Mode",0); draw();
            captured=true; surfaceCaptured=true;
            render(destination.textures[0],destination.framebuffer,true);
        }
    }
    private boolean project(Visible entry,double centerX,double centerY,double centerZ,double extent) {
        if(!screenBounds.project(viewProjection,opticalEye,centerX,centerY,centerZ,extent,width,height)) return false;
        entry.left=screenBounds.left; entry.bottom=screenBounds.bottom;
        entry.right=screenBounds.right; entry.top=screenBounds.top;
        int padding=effectiveGlow(entry.hole)>0?GlBloom.padding(BlackHoleClient.steps):0;
        entry.glowLeft=Math.max(0,entry.left-padding); entry.glowBottom=Math.max(0,entry.bottom-padding);
        entry.glowRight=Math.min(width,entry.right+padding); entry.glowTop=Math.min(height,entry.top+padding);
        return entry.right-entry.left>=2 && entry.top-entry.bottom>=2;
    }
    int finaleDepth() { return captured && opaque.textures[1]!=0?opaque.textures[1]:0; }
    public boolean active() { return (count>0 || openingActive) && !failed; }
    public void matrices(Matrix4f modelView,Matrix4f projection) {
        viewProjection.set(projection).mul(modelView); inverse.set(viewProjection).invert(); inverse.transform(opticalEye.set(0,0,1,0));
        if(Math.abs(opticalEye.w)>1.0e-8) opticalEye.div(opticalEye.w);
    }
    public void beforeTranslucent() {
        if(!active() || OptionalShaders.active()) return;
        var main=Minecraft.getInstance().getMainRenderTarget();
        var itemTarget=Minecraft.useShaderTransparency()?Minecraft.getInstance().levelRenderer.getItemEntityTarget():null;
        captureOpaque(main.getColorTextureId(),main.getDepthTextureId(),main.width,main.height,itemTarget==null?0:itemTarget.getDepthTextureId());
        if(!captured || Minecraft.useShaderTransparency()) return;
        try(var state=GlState.capture()) {
            glBindFramebuffer(GL_FRAMEBUFFER,main.frameBufferId); glDisable(GL_SCISSOR_TEST); glColorMask(false,false,false,true);
            glClearBufferfv(GL_COLOR,0,CLEAR_LIGHT_ZERO); alphaBorrowed=true;
        } catch(RuntimeException exception) { fail(exception); }
    }
    private static final float[] CLEAR_LIGHT_ZERO={0,0,0,0};
    public void afterTranslucent() {
        if(!captured || OptionalShaders.active()) return;
        RenderTarget target=Minecraft.useShaderTransparency()?Minecraft.getInstance().levelRenderer.getTranslucentTarget():Minecraft.getInstance().getMainRenderTarget();
        if(target==null) return;
        captureSurface(target.getColorTextureId(),target.getDepthTextureId(),1);
        restoreAlpha();
    }
    private void restoreAlpha() {
        if(!alphaBorrowed) return;
        alphaBorrowed=false;
        try(var state=GlState.capture()) {
            GlState.fullscreen(); var main=Minecraft.getInstance().getMainRenderTarget(); glBindFramebuffer(GL_FRAMEBUFFER,main.frameBufferId); glViewport(0,0,width,height);
            glColorMask(false,false,false,true); copy.use(); copy.texture("Source",0,opaque.textures[0]); copy.texture("Depth",1,opaque.textures[1]); copy.integer("Mode",0); draw();
        }
    }
    public void captureOpaque(int color,int depth,int targetWidth,int targetHeight) {
        captureOpaque(color,depth,targetWidth,targetHeight,0);
    }
    private void captureOpaque(int color,int depth,int targetWidth,int targetHeight,int extraDepth) {
        if(!active()) return;
        try(var state=GlState.capture()) {
            timing.start(0); initialize(targetWidth,targetHeight); GlState.fullscreen();
            opaque.bind(); copy.use(); copy.texture("Source",0,color); copy.texture("Depth",1,depth); copy.integer("Mode",0); copy.integer("MergeDepth",extraDepth==0?0:1); copy.texture("ExtraDepth",2,extraDepth==0?depth:extraDepth); draw(); copy.integer("MergeDepth",0); captured=true;
        } catch(RuntimeException exception) { fail(exception); }
        finally { timing.end(0); }
    }
    public void captureSurface(int transmittance,int depth,int mode) {
        if(!captured || !active()) return;
        try(var state=GlState.capture()) {
            timing.start(1); GlState.fullscreen(); transparent.bind(); copy.use(); copy.texture("Source",0,transmittance); copy.texture("Depth",1,depth); copy.integer("Mode",mode); draw(); surfaceCaptured=true;
        } catch(RuntimeException exception) { fail(exception); }
        finally { timing.end(1); }
    }
    public void finishVanilla() {
        restoreAlpha();
        if(OptionalShaders.active() || !captured) return;
        var main=Minecraft.getInstance().getMainRenderTarget(); render(main.getColorTextureId(),main.frameBufferId,true);
    }
    public void finishOculus(int color) {
        if(!captured || !surfaceCaptured || !active()) return;
        try(var state=GlState.capture()) {
            glBindFramebuffer(GL_FRAMEBUFFER,outputFramebuffer); glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,color,0);
            glDrawBuffer(GL_COLOR_ATTACHMENT0);
            if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Unsupported Oculus output attachment");
            render(color,outputFramebuffer,false);
            glBindFramebuffer(GL_FRAMEBUFFER,outputFramebuffer); glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,0,0);
        } catch(RuntimeException exception) { fail(exception); }
    }
    private void render(int color,int framebuffer,boolean displaySpace) {
        if(!surfaceCaptured || !active()) return;
        try(var state=GlState.capture()) {
            timing.start(2); GlState.fullscreen();
            if(!displaySpace) { foreground.bind(); glClearBufferfv(GL_COLOR,0,CLEAR_LIGHT); }
            if(openingActive && !openingApplied) {
                opening.render(color,opaque.textures[0],opaque.textures[1],transparent.textures[1],transparent.textures[0],
                        width,height,BlackHoleClient.steps,vertexArray,openingUniforms);
                glBindFramebuffer(GL_READ_FRAMEBUFFER,opening.result.framebuffer);glReadBuffer(GL_COLOR_ATTACHMENT0);
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER,framebuffer);
                glBlitFramebuffer(0,0,width,height,0,0,width,height,GL_COLOR_BUFFER_BIT,GL_NEAREST);
                openingApplied=true;
            }
            for(int index=0;index<count;index++) {
                Visible entry=visible[index]; BlackHole hole=entry.hole;
                prepareGlare(entry);
                scene.bind(); copy.use(); copy.texture("Source",0,color); copy.texture("Depth",1,opaque.textures[1]); copy.integer("Mode",0); draw();
                solution.bind(); glClearBufferfv(GL_COLOR,0,CLEAR_LIGHT); glClearBufferfv(GL_COLOR,1,CLEAR_LIGHT); glClearBufferfv(GL_COLOR,2,CLEAR_LIGHT_ZERO); glClearBufferfv(GL_COLOR,3,CLEAR_META);
                scissor(entry,solution.width,solution.height); integrate.use(); material(integrate,hole); draw(); glDisable(GL_SCISSOR_TEST);
                if(FinaleClient.source(hole.id())) FinaleRenderer.INSTANCE.nearGeometry(opaque.textures[1],transparent.textures[1],solution.width,solution.height,partial);
                if(effectiveGlow(hole)>0) { prepareBlur(hole); renderBloom(entry,displaySpace?1:2); }
                if(glare.enabled()) {
                    GlProgram sourceProgram=glare.begin(); material(sourceProgram,hole);
                    sourceProgram.texture("Transmission",2,transparent.textures[0]); draw();
                }
                glBindFramebuffer(GL_FRAMEBUFFER,framebuffer); glViewport(0,0,width,height); glowScissor(entry);
                prepareComposite(hole,displaySpace,displaySpace?0:1); draw(); glDisable(GL_SCISSOR_TEST);
                if(!displaySpace) {
                    if(effectiveGlow(hole)>0) { prepareBlur(hole); renderBloom(entry,3); }
                    foreground.bind(); glowScissor(entry); prepareComposite(hole,false,2);
                    glEnable(GL_BLEND); glBlendEquationSeparate(GL_FUNC_ADD,GL_FUNC_ADD); glBlendFuncSeparate(GL_ONE,GL_SRC_ALPHA,GL_ZERO,GL_SRC_ALPHA); draw(); glDisable(GL_BLEND); glDisable(GL_SCISSOR_TEST);
                }
            }
        } catch(RuntimeException exception) { fail(exception); }
        finally { timing.end(2); }
    }
    private void openingUniforms(GlProgram program) { material(program,FinaleClient.state().hole()); }
    public void clearOpening() {
        try(var state=GlState.capture()) { opening.close(); }
        openingActive=false;openingApplied=false;
    }
    private void prepareBlur(BlackHole hole) {
        blur.use(); material(blur,hole); blur.texture("FrontTexture",0,solution.textures[0]); blur.texture("BackTexture",1,solution.textures[1]); blur.texture("Transmission",2,transparent.textures[0]); blur.texture("LensTexture",3,solution.textures[2]);
        blur.pair("FullSize",width,height);
    }
    private void prepareGlare(Visible entry) {
        entry.compositeLeft=entry.glowLeft; entry.compositeBottom=entry.glowBottom;
        entry.compositeRight=entry.glowRight; entry.compositeTop=entry.glowTop;
        glare.disable();
        BlackHole hole=entry.hole;
        if(!FinaleClient.source(hole.id())) return;
        var frame=FinaleClient.frame(partial);
        if(!glare.project(viewProjection,opticalEye,(float)(hole.x()-cameraX),(float)(hole.y()-cameraY),(float)(hole.z()-cameraZ),
                hole.value("radius"),FinaleRenderer.nearAmount(hole.id(),partial),hole.value("glow"),hole.value("brightness"),
                frame.phase().ordinal(),frame.progress(),width,height,CausalUniforms.glare(hole.id(),partial))) return;
        entry.compositeLeft=Math.min(entry.compositeLeft,glare.left); entry.compositeBottom=Math.min(entry.compositeBottom,glare.bottom);
        entry.compositeRight=Math.max(entry.compositeRight,glare.right); entry.compositeTop=Math.max(entry.compositeTop,glare.top);
    }
    private void renderBloom(Visible entry,int mode) {
        bloom.render(blur,mode,vertexArray,entry.glowLeft/(float)width,entry.glowBottom/(float)height,entry.glowRight/(float)width,entry.glowTop/(float)height);
    }
    private void prepareComposite(BlackHole hole,boolean displaySpace,int mode) {
        composite.use(); material(composite,hole);
        composite.texture("Scene",0,scene.textures[0]); composite.texture("Opaque",1,openingApplied?opening.result.textures[1]:opaque.textures[0]); composite.texture("Transmission",2,transparent.textures[0]);
        composite.texture("FrontTexture",3,solution.textures[0]); composite.texture("BackTexture",4,solution.textures[1]); composite.texture("LensTexture",5,solution.textures[2]); composite.texture("MetaTexture",6,solution.textures[3]); composite.texture("Bloom",7,bloom.result().textures[0]);
        composite.scalar("Glow",effectiveGlow(hole)); composite.integer("DisplaySpace",displaySpace?1:0); composite.integer("LayerMode",mode);
        glare.bind(composite);
    }
    public void afterOculusRefraction(int color,int framebuffer) {
        if(!active() || !captured || foregroundComposite==null) return;
        try(var state=GlState.capture()) {
            timing.start(3); GlState.fullscreen();
            scene.bind(); copy.use(); copy.texture("Source",0,color); copy.texture("Depth",1,opaque.textures[1]); copy.integer("Mode",0); draw();
            glBindFramebuffer(GL_FRAMEBUFFER,framebuffer); glViewport(0,0,width,height); foregroundComposite.use(); foregroundComposite.texture("Scene",0,scene.textures[0]); foregroundComposite.texture("Foreground",1,foreground.textures[0]); draw();
        } catch(RuntimeException exception) { fail(exception); }
        finally { timing.end(3); }
    }
    private void material(GlProgram program,BlackHole hole) {
        program.matrix("VP",viewProjection); program.matrix("InverseVP",inverse); program.vector("Eye",opticalEye.x,opticalEye.y,opticalEye.z);
        program.vector("Center",(float)(hole.x()-cameraX),(float)(hole.y()-cameraY),(float)(hole.z()-cameraZ));
        double tilt=Math.toRadians(hole.value("tilt")), yaw=Math.toRadians(hole.value("yaw"));
        program.vector("DiskNormal",(float)(Math.sin(yaw)*Math.sin(tilt)),(float)Math.cos(tilt),(float)(Math.cos(yaw)*Math.sin(tilt)));
        program.vector("DiskRight",(float)Math.cos(yaw),0,(float)-Math.sin(yaw));
        program.vector("DiskForward",(float)(-Math.sin(yaw)*Math.cos(tilt)),(float)Math.sin(tilt),(float)(-Math.cos(yaw)*Math.cos(tilt)));
        color(program,"HotColor",(int)hole.value("hotColor")); color(program,"ColdColor",(int)hole.value("coldColor"));
        for(int index=0;index<MATERIAL.length;index++) program.scalar(UNIFORMS[index],hole.value(MATERIAL[index]));
        program.scalar("Glow",effectiveGlow(hole));
        FinaleRenderer.nearUniforms(program,hole.id(),partial);
        program.scalar("Time",hole.time(time)%1048576.0); program.scalar("Seed",(hole.seed()&65535)/257.0); program.integer("Steps",BlackHoleClient.steps);
        program.scalar("PixelAngle",pixelAngle/(program==integrate?BlackHoleClient.resolution:1.0f));
        program.texture("DiskTexture",10,diskTexture.handle());
        program.texture("OpaqueDepth",8,opaque.textures[1]); program.texture("SurfaceDepth",9,transparent.textures[1]);
    }
    private static void color(GlProgram program,String name,int color) { program.vector(name,((color>>16)&255)/255.0f,((color>>8)&255)/255.0f,(color&255)/255.0f); }
    private double effectiveGlow(BlackHole hole) {
        return FinaleClient.source(hole.id())?Math.max(hole.value("glow"),FinaleClient.pulse(partial)*3):hole.value("glow");
    }
    private void scissor(Visible entry,int targetWidth,int targetHeight) {
        double scaleX=targetWidth/(double)width, scaleY=targetHeight/(double)height;
        int left=(int)(entry.left*scaleX), bottom=(int)(entry.bottom*scaleY);
        glEnable(GL_SCISSOR_TEST); glScissor(left,bottom,(int)Math.ceil(entry.right*scaleX)-left,(int)Math.ceil(entry.top*scaleY)-bottom);
    }
    private void draw() { glBindVertexArray(vertexArray); glDrawArrays(GL_TRIANGLES,0,3); }
    private static void glowScissor(Visible entry) {
        glEnable(GL_SCISSOR_TEST); glScissor(entry.compositeLeft,entry.compositeBottom,entry.compositeRight-entry.compositeLeft,entry.compositeTop-entry.compositeBottom);
    }
    private void initialize(int targetWidth,int targetHeight) {
        width=targetWidth; height=targetHeight;
        if(copy==null) { copy=new GlProgram("copy"); integrate=new GlProgram("integrate"); composite=new GlProgram("composite"); blur=new GlProgram("bloom"); foregroundComposite=new GlProgram("foreground"); vertexArray=glGenVertexArrays(); outputFramebuffer=glGenFramebuffers(); }
        if(diskTexture==null) diskTexture=GlDiskTexture.load();
        opaque.resize(width,height); transparent.resize(width,height); scene.resize(width,height);
        solution.resize(Math.max(1,(int)Math.ceil(width*BlackHoleClient.resolution)),Math.max(1,(int)Math.ceil(height*BlackHoleClient.resolution)));
        bloom.resize(width,height,BlackHoleClient.steps);
        if(!cosmic) foreground.resize(width,height);
    }
    private void fail(RuntimeException exception) { if(!failed) PatchLog.LOGGER.error("Black hole rendering suspended until resource reload; path={}",path,exception); failed=true; count=0; if(cosmic) throw exception; if(FinaleClient.active()) FinaleClient.fail(exception); }
    public void suspend(String reason) { fail(new IllegalStateException(reason)); }
    @Override public void close() {
        if(!cosmic) OptionalShaders.reset();
        restoreAlpha(); opaque.close(); transparent.close(); scene.close(); solution.close(); bloom.close(); glare.close(); opening.close(); openingActive=false;openingApplied=false;foreground.close(); timing.close();
        if(copy!=null) copy.close(); if(integrate!=null) integrate.close(); if(composite!=null) composite.close(); if(blur!=null) blur.close();
        if(foregroundComposite!=null) foregroundComposite.close(); foregroundComposite=null;
        copy=null; integrate=null; composite=null; blur=null;
        if(diskTexture!=null) diskTexture.close(); diskTexture=null;
        if(vertexArray!=0) glDeleteVertexArrays(vertexArray); if(outputFramebuffer!=0) glDeleteFramebuffers(outputFramebuffer);
        vertexArray=0; outputFramebuffer=0; count=0; captured=false; surfaceCaptured=false; failed=false; level=null;
        for(var entry:visible) entry.hole=null;
    }
    private static final class Visible { BlackHole hole; int left,bottom,right,top,glowLeft,glowBottom,glowRight,glowTop,compositeLeft,compositeBottom,compositeRight,compositeTop; double distance; }
}
