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
    private final GlTarget foreground = new GlTarget(GL_RGBA16F), subjectFront=new GlTarget(GL_RGBA16F);
    private final GlRect solutionRect=new GlRect();
    private final DiskAdvection diskAdvection=new DiskAdvection();
    private long emptySince;
    private GlProgram frontResolve,reconstructionCount,compositeFull,blurFull,frontFull,countFull,activeBlur;
    private final Visible[] visible = new Visible[256];
    private final boolean cosmic;
    private float partial;
    private GlDiskTexture diskTexture;
    private float pixelAngle;
    private double solutionScale;
    private GlProgram copy, integrate, composite, blur, foregroundComposite;
    private int vertexArray, outputFramebuffer, count, width, height;
    private boolean captured, surfaceCaptured, alphaBorrowed, failed, cleanupPending;
    private boolean shaderHigh;
    private Object level;
    private double cameraX, cameraY, cameraZ, time;
    private String path = "vanilla";
    private final GpuDiagnostics timing = new GpuDiagnostics();
    BlackHoleRenderer(boolean cosmic) { this.cosmic=cosmic; for (int index=0; index<visible.length; index++) visible[index]=new Visible(); }

    public void begin(Matrix4f modelView, Matrix4f projection, Camera camera, float partial) {
        if (OptionalShaders.shadow()) return;
        // A failure can occur inside the adapter's FBO scope. Release only at the next
        // host boundary, so an outer GlState never restores a framebuffer we just deleted.
        if(cleanupPending) {
            Object previousLevel=level;
            try(var state=GlState.capture()) { close(); } finally { failed=true;level=previousLevel; }
        }
        restoreAlpha();
        if (level != Minecraft.getInstance().level) { close(); level=Minecraft.getInstance().level; }
        captured=false; surfaceCaptured=false; count=0; openingActive=false; openingApplied=false;
        boolean empty=BlackHoleClient.records().isEmpty() && !FinaleClient.active();
        if(empty) {
            long now=System.nanoTime();if(emptySince==0) emptySince=now;
            if(now-emptySince>=30_000_000_000L && scene.framebuffer!=0) try(var state=GlState.capture()) { releaseTargets(); }
        } else emptySince=0;
        if (failed || level==null || empty || FinaleClient.skipWorld(partial)) return;
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
        timing.frame(BlackHoleClient.diagnostics,active(),path+(FinaleClient.active()?"/"+FinaleClient.frame(partial).phase().id():"/ordinary"),width,height);
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
        if(relativistic(hole)) extent=Math.max(extent,32.0*hole.value("radius")*hole.value("lensing")/Math.sqrt(27.0));
        if(FinaleClient.source(hole.id())) {
            extent*=Math.max(1,CausalUniforms.bodyScale(hole.id(),partial));
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
        timing.frame(BlackHoleClient.diagnostics,count>0,path+"/"+FinaleClient.frame(partial).phase().id(),width,height);
        if(count==0) return;
        try(var state=GlState.capture()) {
            timing.start("cosmic-prepare");initialize(width,height); GlState.fullscreen();
            transparent.bind(); glClearBufferfv(GL_COLOR,0,UNIT_TRANSMISSION); glClearBufferfv(GL_COLOR,1,FAR_DEPTH);
            timing.end("cosmic-prepare");
            captured=true; surfaceCaptured=true;
            render(destination.textures[0],destination.framebuffer,true);
        } finally { timing.end("cosmic-prepare"); }
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
        long submitStart=System.nanoTime();
        try(var state=GlState.capture()) {
            timing.start("opaque-capture"); initialize(targetWidth,targetHeight); GlState.fullscreen();
            opaque.bind(); copy.use(); copy.texture("Source",0,color); copy.texture("Depth",1,depth); copy.integer("Mode",0); copy.integer("MergeDepth",extraDepth==0?0:1); copy.texture("ExtraDepth",2,extraDepth==0?depth:extraDepth); draw(); copy.integer("MergeDepth",0); captured=true;
        } catch(RuntimeException exception) { fail(exception); }
        finally { timing.end("opaque-capture");timing.cpu("capture-with-state",(System.nanoTime()-submitStart)/1e6); }
    }
    public void captureSurface(int transmittance,int depth,int mode) {
        if(!captured || !active()) return;
        try(var state=GlState.capture()) {
            timing.start("surface-capture"); GlState.fullscreen(); transparent.bind(); copy.use(); copy.texture("Source",0,transmittance); copy.texture("Depth",1,depth); copy.integer("Mode",mode); draw(); surfaceCaptured=true;
        } catch(RuntimeException exception) { fail(exception); }
        finally { timing.end("surface-capture"); }
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
        long submitStart=System.nanoTime();
        try(var state=GlState.capture()) {
            timing.start("subjects-total"); GlState.fullscreen();
            if(!displaySpace) { foreground.bind(); glClearBufferfv(GL_COLOR,0,CLEAR_LIGHT); }
            if(openingActive && !openingApplied) {
                timing.start("opening");opening.render(color,opaque.textures[0],width,height,vertexArray,openingUniforms);
                glBindFramebuffer(GL_READ_FRAMEBUFFER,opening.result.framebuffer);glReadBuffer(GL_COLOR_ATTACHMENT0);
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER,framebuffer);
                glBlitFramebuffer(0,0,width,height,0,0,width,height,GL_COLOR_BUFFER_BIT,GL_NEAREST);
                openingApplied=true;timing.end("opening");
            }
            // One immutable full snapshot; subsequent subjects refresh only the previous write rect.
            timing.start("scene-copy");copyScene(color,null);timing.end("scene-copy");
            long pixels=0;
            for(int index=0;index<count;index++) {
                Visible entry=visible[index]; BlackHole hole=entry.hole;
                prepareGlare(entry);
                if(index>0) { timing.start("scene-copy");copyScene(color,visible[index-1]);timing.end("scene-copy"); }
                solutionRect.normalized(entry.left/(float)width-2f/solution.width,entry.bottom/(float)height-2f/solution.height,
                    entry.right/(float)width+2f/solution.width,entry.top/(float)height+2f/solution.height,solution.width,solution.height);
                timing.start("solve");solution.bind();if(solutionRect.full(solution.width,solution.height)) glDisable(GL_SCISSOR_TEST);else solutionRect.scissor();
                glClearBufferfv(GL_COLOR,0,CLEAR_LIGHT);glClearBufferfv(GL_COLOR,1,CLEAR_LIGHT);
                glClearBufferfv(GL_COLOR,2,CLEAR_LIGHT_ZERO);glClearBufferfv(GL_COLOR,3,CLEAR_META);
                integrate.use();material(integrate,hole);draw();
                glDisable(GL_SCISSOR_TEST);timing.end("solve");
                if(effectiveGlow(hole)>0) { timing.start("bloom-back");prepareBlur(hole);renderBloom(entry,displaySpace?1:2);timing.end("bloom-back"); }
                if(glare.enabled()) {
                    timing.start("glare-source");GlProgram sourceProgram=glare.begin();material(sourceProgram,hole);
                    sourceProgram.texture("Transmission",2,transparent.textures[0]);draw();timing.end("glare-source");
                }
                glBindFramebuffer(GL_FRAMEBUFFER,framebuffer);glViewport(0,0,width,height);glowScissor(entry);
                if(!displaySpace) {
                    glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT1,GL_TEXTURE_2D,subjectFront.textures[0],0);
                    glDrawBuffers(new int[]{GL_COLOR_ATTACHMENT0,GL_COLOR_ATTACHMENT1});
                    if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Split subject framebuffer incomplete");
                }
                try { timing.start("reconstruct-compose");prepareComposite(hole,displaySpace,displaySpace?0:1);draw();timing.end("reconstruct-compose"); }
                finally { if(!displaySpace) { glDrawBuffer(GL_COLOR_ATTACHMENT0);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT1,GL_TEXTURE_2D,0,0); } }
                classify(entry,hole);
                glDisable(GL_SCISSOR_TEST);
                if(!displaySpace) {
                    if(effectiveGlow(hole)>0) { timing.start("bloom-front");prepareBlur(hole);renderBloom(entry,3);timing.end("bloom-front"); }
                    timing.start("front-accumulate");foreground.bind();glowScissor(entry);
                    GlProgram front=frontResolve;
                    if(solutionRect.full(solution.width,solution.height)) { if(frontFull==null) frontFull=specialized("subject_front");front=frontFull; }
                    front.use();material(front,hole);front.integer("LayerMode",2);
                    front.texture("CachedSubject",3,subjectFront.textures[0]);front.texture("Transmission",2,transparent.textures[0]);
                    front.texture("Bloom",7,bloom.result().textures[0]);bloom.bounds(front);glare.bind(front);
                    glEnable(GL_BLEND);glBlendEquationSeparate(GL_FUNC_ADD,GL_FUNC_ADD);glBlendFuncSeparate(GL_ONE,GL_SRC_ALPHA,GL_ZERO,GL_SRC_ALPHA);
                    draw();glDisable(GL_BLEND);glDisable(GL_SCISSOR_TEST);timing.end("front-accumulate");
                }
                pixels+=(long)(entry.compositeRight-entry.compositeLeft)*(entry.compositeTop-entry.compositeBottom);
            }
            timing.metrics(count,pixels,ownedBytes());
        } catch(RuntimeException exception) { fail(exception); }
        finally { timing.end("subjects-total");timing.cpu("subjects-with-state",(System.nanoTime()-submitStart)/1e6); }
    }
    private GlProgram specialized(String name) {
        long started=System.nanoTime();
        try { return new GlProgram(name,true,shaderHigh); } finally { timing.cpu("programs",(System.nanoTime()-started)/1e6); }
    }
    private int depthTexture() { return cosmic?transparent.textures[1]:opaque.textures[1]; }
    private void copyScene(int color,Visible previous) {
        scene.bind();if(previous==null) glDisable(GL_SCISSOR_TEST);else glowScissor(previous);
        copy.use();copy.texture("Source",0,color);copy.texture("Depth",1,depthTexture());copy.integer("Mode",0);draw();glDisable(GL_SCISSOR_TEST);
    }
    private void classify(Visible entry,BlackHole hole) {
        if(!timing.classify()) return;
        if(reconstructionCount==null) reconstructionCount=new GlProgram("reconstruction_count",false,shaderHigh);
        GlProgram classifier=reconstructionCount;
        if(solutionRect.full(solution.width,solution.height)) { if(countFull==null) countFull=specialized("reconstruction_count");classifier=countFull; }
        timing.start("diagnostic-classify");classifier.use();material(classifier,hole);
        classifier.texture("FrontTexture",3,solution.textures[0]);classifier.texture("BackTexture",4,solution.textures[1]);classifier.texture("MetaTexture",6,solution.textures[3]);
        glColorMask(false,false,false,false);
        try {
            for(int mode=0;mode<2;mode++) {
                String name=mode==0?"eligible":"fallback";classifier.integer("FallbackOnly",mode);timing.countStart(name);
                try { draw(); } finally { timing.countEnd(name); }
            }
        } finally { glColorMask(true,true,true,true);timing.end("diagnostic-classify"); }
    }
    private long ownedBytes() { return opaque.bytes()+transparent.bytes()+scene.bytes()+solution.bytes()+foreground.bytes()+subjectFront.bytes()+bloom.bytes()+opening.result.bytes()+glare.source.bytes(); }
    private void releaseTargets() { opaque.close();transparent.close();scene.close();solution.close();foreground.close();subjectFront.close();bloom.close();opening.result.close();captured=false;surfaceCaptured=false; }
    private void openingUniforms(GlProgram program) { material(program,FinaleClient.state().hole()); }
    public void clearOpening() {
        try(var state=GlState.capture()) { opening.close(); }
        openingActive=false;openingApplied=false;
    }
    private void prepareBlur(BlackHole hole) {
        activeBlur=blur;
        if(solutionRect.full(solution.width,solution.height)) { if(blurFull==null) blurFull=specialized("bloom");activeBlur=blurFull; }
        activeBlur.use(); material(activeBlur,hole); activeBlur.texture("FrontTexture",0,solution.textures[0]); activeBlur.texture("BackTexture",1,solution.textures[1]); activeBlur.texture("Transmission",2,transparent.textures[0]); activeBlur.texture("LensTexture",3,solution.textures[2]);
        activeBlur.pair("FullSize",width,height);
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
        bloom.render(activeBlur,mode,vertexArray,entry.glowLeft/(float)width,entry.glowBottom/(float)height,entry.glowRight/(float)width,entry.glowTop/(float)height);
    }
    private void prepareComposite(BlackHole hole,boolean displaySpace,int mode) {
        GlProgram p=composite;
        if(solutionRect.full(solution.width,solution.height)) { if(compositeFull==null) compositeFull=specialized("composite");p=compositeFull; }
        p.use(); material(p,hole);
        p.texture("Scene",0,scene.textures[0]); p.texture("Opaque",1,openingApplied?opening.result.textures[1]:cosmic?scene.textures[0]:opaque.textures[0]); p.texture("Transmission",2,transparent.textures[0]);
        p.texture("FrontTexture",3,solution.textures[0]); p.texture("BackTexture",4,solution.textures[1]); p.texture("LensTexture",5,solution.textures[2]); p.texture("MetaTexture",6,solution.textures[3]); p.texture("Bloom",7,bloom.result().textures[0]);
        p.scalar("Glow",effectiveGlow(hole)); p.integer("DisplaySpace",displaySpace?1:0); p.integer("LayerMode",mode);
        bloom.bounds(p);glare.bind(p);
    }
    public void afterOculusRefraction(int color,int framebuffer) {
        if(!active() || !captured || foregroundComposite==null) return;
        try(var state=GlState.capture()) {
            timing.start("host-front-compose"); GlState.fullscreen();
            scene.bind(); copy.use(); copy.texture("Source",0,color); copy.texture("Depth",1,opaque.textures[1]); copy.integer("Mode",0); draw();
            glBindFramebuffer(GL_FRAMEBUFFER,framebuffer); glViewport(0,0,width,height); foregroundComposite.use(); foregroundComposite.texture("Scene",0,scene.textures[0]); foregroundComposite.texture("Foreground",1,foreground.textures[0]); draw();
        } catch(RuntimeException exception) { fail(exception); }
        finally { timing.end("host-front-compose"); }
    }
    private void material(GlProgram program,BlackHole hole) {
        program.integer("Relativistic",relativistic(hole)?1:0);
        program.scalar("Spin",BlackHoleClient.spin);
        program.integer("SkipFieldLens",1);program.pair("SceneSize",width,height);program.matrix("VP",viewProjection); program.matrix("InverseVP",inverse); program.vector("Eye",opticalEye.x,opticalEye.y,opticalEye.z);
        program.vector("Center",(float)(hole.x()-cameraX),(float)(hole.y()-cameraY),(float)(hole.z()-cameraZ));
        double tilt=Math.toRadians(hole.value("tilt")), yaw=Math.toRadians(hole.value("yaw"));
        program.vector("DiskNormal",(float)(Math.sin(yaw)*Math.sin(tilt)),(float)Math.cos(tilt),(float)(Math.cos(yaw)*Math.sin(tilt)));
        program.vector("DiskRight",(float)Math.cos(yaw),0,(float)-Math.sin(yaw));
        program.vector("DiskForward",(float)(-Math.sin(yaw)*Math.cos(tilt)),(float)Math.sin(tilt),(float)(-Math.cos(yaw)*Math.cos(tilt)));
        color(program,"HotColor",(int)hole.value("hotColor")); color(program,"ColdColor",(int)hole.value("coldColor"));
        for(int index=0;index<MATERIAL.length;index++) program.scalar(UNIFORMS[index],hole.value(MATERIAL[index]));
        program.scalar("Glow",effectiveGlow(hole));
        FinaleRenderer.nearUniforms(program,hole.id(),partial);
        diskAdvection.evaluate(hole.time(time),hole.value("rotation"),hole.value("infall"),hole.value("outer")-hole.value("inner"),hole.seed());diskAdvection.apply(program); program.scalar("Seed",(hole.seed()&65535)/257.0); program.integer("Steps",BlackHoleClient.steps);
        program.scalar("PixelAngle",pixelAngle/(program==integrate?solutionScale:1.0f));
        program.texture("DiskTexture",10,diskTexture.handle());
        program.integer("SolutionBounded",solutionRect.full(solution.width,solution.height)?0:1);solutionRect.upload(program,"SolutionBounds");
        program.texture("OpaqueDepth",8,depthTexture()); program.texture("SurfaceDepth",9,transparent.textures[1]);
    }
    private static void color(GlProgram program,String name,int color) { program.vector(name,((color>>16)&255)/255.0f,((color>>8)&255)/255.0f,(color&255)/255.0f); }
    private static boolean relativistic(BlackHole hole) { return BlackHoleClient.steps>=96; }
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
        long cpuStart=System.nanoTime();
        width=targetWidth; height=targetHeight;
        if(copy==null) { copy=new GlProgram("copy"); foregroundComposite=new GlProgram("foreground"); vertexArray=glGenVertexArrays(); outputFramebuffer=glGenFramebuffers(); }
        if(integrate==null || shaderHigh!=(BlackHoleClient.steps>=96)) {
            closeFieldPrograms();shaderHigh=BlackHoleClient.steps>=96;
            integrate=new GlProgram("integrate",false,shaderHigh);composite=new GlProgram("composite",false,shaderHigh);blur=new GlProgram("bloom",false,shaderHigh);
        }
        if(!cosmic && OptionalShaders.active() && frontResolve==null) frontResolve=new GlProgram("subject_front",false,shaderHigh);
        timing.cpu("programs",(System.nanoTime()-cpuStart)/1e6);cpuStart=System.nanoTime();
        if(diskTexture==null) diskTexture=GlDiskTexture.load();
        timing.cpu("material-upload",(System.nanoTime()-cpuStart)/1e6);cpuStart=System.nanoTime();
        if(!cosmic) opaque.resize(width,height);
        transparent.resize(cosmic?1:width,cosmic?1:height);scene.resize(width,height);
        solutionScale=BlackHoleClient.resolution;
        for(int index=0;index<count;index++) if(relativistic(visible[index].hole)) { solutionScale=1.;break; }
        solution.resize(Math.max(1,(int)Math.ceil(width*solutionScale)),Math.max(1,(int)Math.ceil(height*solutionScale)));
        bloom.resize(width,height,BlackHoleClient.steps);
        if(!cosmic && OptionalShaders.active()) {
            foreground.resize(width,height);subjectFront.resize(width,height);
        } else { foreground.close();subjectFront.close(); }
        timing.cpu("targets",(System.nanoTime()-cpuStart)/1e6);
    }
    private void fail(RuntimeException exception) {
        if(!failed) PatchLog.LOGGER.error("Black hole rendering suspended until resource reload; path={}",path,exception);
        failed=true;cleanupPending=true;count=0;
        if(cosmic) throw exception;if(FinaleClient.active()) FinaleClient.fail(exception);
    }
    public void suspend(String reason) { fail(new IllegalStateException(reason)); }
    @Override public void close() {
        if(!cosmic) OptionalShaders.reset();
        restoreAlpha(); opaque.close(); transparent.close(); scene.close(); solution.close(); bloom.close(); glare.close(); opening.close(); openingActive=false;openingApplied=false;foreground.close();subjectFront.close(); timing.close();
        if(copy!=null) copy.close();closeFieldPrograms();
        if(foregroundComposite!=null) foregroundComposite.close(); foregroundComposite=null;
        emptySince=0;
        copy=null; integrate=null; composite=null; blur=null;
        if(diskTexture!=null) diskTexture.close(); diskTexture=null;
        if(vertexArray!=0) glDeleteVertexArrays(vertexArray); if(outputFramebuffer!=0) glDeleteFramebuffers(outputFramebuffer);
        vertexArray=0; outputFramebuffer=0; count=0; captured=false; surfaceCaptured=false; failed=false;cleanupPending=false; level=null;
        for(var entry:visible) entry.hole=null;
    }
    private void closeFieldPrograms() {
        for(GlProgram p:new GlProgram[]{integrate,composite,blur,frontResolve,reconstructionCount,compositeFull,blurFull,frontFull,countFull})if(p!=null)p.close();
        integrate=composite=blur=frontResolve=reconstructionCount=compositeFull=blurFull=frontFull=countFull=activeBlur=null;
    }
    private static final class Visible { BlackHole hole; int left,bottom,right,top,glowLeft,glowBottom,glowRight,glowTop,compositeLeft,compositeBottom,compositeRight,compositeTop; double distance; }
}
