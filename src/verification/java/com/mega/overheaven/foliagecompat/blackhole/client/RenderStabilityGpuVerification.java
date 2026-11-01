package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.glfw.GLFW.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.BufferUtils;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import java.nio.file.*;
import java.util.*;
import com.mega.overheaven.foliagecompat.finale.*;

/** Production GLSL/resource classes in a hidden GL context; no Minecraft launch or host certification. */
public final class RenderStabilityGpuVerification {
    private static Path source,before;static int vao;private static int checks;
    private static void check(boolean yes,String text) { if(!yes) throw new AssertionError(text);checks++; }
    private static GlProgram shader(Path dir,String name)throws Exception {return new GlProgram(VerificationShaders.read(dir,"fullscreen.vsh"),VerificationShaders.read(dir,name+".fsh"));}
    private static void draw() {glBindVertexArray(vao);glDrawArrays(GL_TRIANGLES,0,3);}
    private static final String FIXTURE="""
        #version 330 core
        in vec2 texCoord;layout(location=0) out vec4 Color;layout(location=1) out float Depth;
        uniform float NearDepth,FarDepth,Surface;uniform int Mode,Shape;uniform vec2 Size;
        void main(){
            vec2 p=texCoord;float lines=step(.5,fract((p.x+p.y*.2)*35.));
            vec3 c=vec3(.06+.45*p.x,.08+.35*p.y,.15+.14*lines);
            bool block=Shape==1?fract(gl_FragCoord.x/5.)<.2:Shape==2?sin(p.x*139.)*cos(p.y*103.)>.3:Shape==3;
            Depth=block?NearDepth:FarDepth;
            if(Mode==0) Color=vec4(c,1);
            else if(Mode==1){Color=vec4(p.x<.6?vec3(.45,.62,.8):vec3(1),1);Depth=p.x<.6?Surface:Depth;}
            else Color=vec4(p.x<.6?c*vec3(.45,.62,.8)+vec3(.025,.018,.02):c,1);
        }
        """;
private static final class OldBloom implements AutoCloseable {
    private static final float[] EMPTY={0,0,0,65504};
    private final GlTarget[] down=new GlTarget[5], up=new GlTarget[4];
    private int levels;
    OldBloom() {
        for(int index=0;index<down.length;index++) down[index]=new GlTarget(GL_RGBA16F).linear();
        for(int index=0;index<up.length;index++) up[index]=new GlTarget(GL_RGBA16F).linear();
    }
    static int levels(int steps) { return steps<=24?3:steps<=48?4:5; }
    static int padding(int steps) { return 12*((1<<levels(steps))-1)+8; }
    void resize(int width,int height,int steps) {
        levels=levels(steps);
        for(int index=0;index<down.length;index++) {
            if(index<levels) {
                int divisor=4<<index;
                down[index].resize(Math.max(1,(width+divisor-1)/divisor),Math.max(1,(height+divisor-1)/divisor));
                if(index<levels-1) up[index].resize(down[index].width,down[index].height);
                else if(index<up.length) up[index].close();
            } else { down[index].close(); if(index<up.length) up[index].close(); }
        }
    }
    GlTarget result() { return up[0]; }
    void render(GlProgram program,int extract,int vertexArray) {
        render(program,extract,vertexArray,0,0,1,1);
    }
    void render(GlProgram program,int extract,int vertexArray,float left,float bottom,float right,float top) {
        glDisable(GL_SCISSOR_TEST); glBindVertexArray(vertexArray);
        program.use(); program.integer("Extract",extract);
        bind(down[0],left,bottom,right,top); glDrawArrays(GL_TRIANGLES,0,3);
        for(int index=1;index<levels;index++) {
            bind(down[index],left,bottom,right,top); program.integer("Extract",0); program.texture("Source",0,down[index-1].textures[0]);
            glDrawArrays(GL_TRIANGLES,0,3);
        }
        for(int index=levels-2;index>=0;index--) {
            GlTarget coarse=index==levels-2?down[index+1]:up[index+1];
            bind(up[index],left,bottom,right,top); program.integer("Extract",4); program.texture("Source",0,coarse.textures[0]);
            program.texture("Fine",4,down[index].textures[0]); program.scalar("CoarseWeight",levels-index-1);
            glDrawArrays(GL_TRIANGLES,0,3);
        }
        glDisable(GL_SCISSOR_TEST);
    }
    private static void bind(GlTarget target,float left,float bottom,float right,float top) {
        target.bind(); glDisable(GL_SCISSOR_TEST); glClearBufferfv(GL_COLOR,0,EMPTY);
        int minX=(int)Math.floor(left*target.width), minY=(int)Math.floor(bottom*target.height);
        glEnable(GL_SCISSOR_TEST); glScissor(minX,minY,(int)Math.ceil(right*target.width)-minX,(int)Math.ceil(top*target.height)-minY);
    }
    @Override public void close() {
        for(var target:down) target.close();
        for(var target:up) target.close();
        levels=0;
    }
}

    static class Pipeline implements AutoCloseable {
        final GlTarget opaque=new GlTarget(GL_RGBA16F,GL_R32F),surface=new GlTarget(GL_RGBA16F,GL_R32F),scene=new GlTarget(GL_RGBA16F),snapshot=new GlTarget(GL_RGBA16F);
        final GlTarget solution=new GlTarget(GL_RGBA16F,GL_RGBA16F,GL_RGBA32F,GL_RGBA32F),out=new GlTarget(GL_RGBA16F,GL_RGBA16F),front=new GlTarget(GL_RGBA16F),reference=new GlTarget(GL_RGBA16F),constant=new GlTarget(GL_RGBA16F,GL_R32F);
        final GlBloom bloom=new GlBloom();final OldBloom oldBloom=new OldBloom();GlProgram solve,compose,blur,frontShader;final GlProgram copy,fixture;
        final GlDiskTexture disk;final DiskAdvection advection=new DiskAdvection();final Matrix4f vp=new Matrix4f(),inv=new Matrix4f();final GlRect rect=new GlRect(),glow=new GlRect();
        GlProgram composeFull,blurFull,frontFull;
        final boolean legacy;int width,height,steps=48;float scale=.5f,distance=35,centerX=0,glowStrength=.35f;double time=7;boolean renew=true,constantDepth=false;int shape;boolean split=true,fullCopies=false,cosmicScene=false;
        double finaleTicks=-1;
        final FinaleTimeline timeline=FinaleTimeline.defaults();
        final FinaleWaves.State waves=new FinaleWaves.State();
        final CausalTransition.State intro=new CausalTransition.State();
        Pipeline(boolean legacy)throws Exception { this(legacy,legacy?before:source); }
        Pipeline(boolean legacy,Path dir)throws Exception {
            this.legacy=legacy;
            solve=shader(dir,"integrate");compose=shader(dir,"composite");blur=shader(dir,"bloom");copy=shader(dir,"copy");frontShader=legacy?null:shader(dir,"subject_front");
            composeFull=legacy?null:new GlProgram(VerificationShaders.read(dir,"fullscreen.vsh"),GlProgram.fullTargetSource(VerificationShaders.read(dir,"composite.fsh")));
            blurFull=legacy?null:new GlProgram(VerificationShaders.read(dir,"fullscreen.vsh"),GlProgram.fullTargetSource(VerificationShaders.read(dir,"bloom.fsh")));
            frontFull=legacy?null:new GlProgram(VerificationShaders.read(dir,"fullscreen.vsh"),GlProgram.fullTargetSource(VerificationShaders.read(dir,"subject_front.fsh")));
            fixture=new GlProgram(VerificationShaders.read(dir,"fullscreen.vsh"),FIXTURE);
            try(var stream=Files.newInputStream(dir.resolve("../../textures/blackhole/disk_detail.png").normalize())){disk=new GlDiskTexture(stream);}
            resize(640,360);
        }
        void resize(int w,int h) {
            width=w;height=h;vp.identity().perspective((float)Math.toRadians(70),w/(float)h,.05f,2000);inv.set(vp).invert();
            for(var t:new GlTarget[]{opaque,surface,scene,snapshot,out,front,reference})t.resize(w,h);
            solution.resize((int)Math.ceil(w*scale),(int)Math.ceil(h*scale));constant.resize(1,1);constant.bind();glClearBufferfv(GL_COLOR,0,new float[]{1,1,1,1});glClearBufferfv(GL_COLOR,1,new float[]{1,0,0,0});bloom.resize(w,h,steps);oldBloom.resize(w,h,steps);
            inputs();
        }
        float depth(float distance){Vector4f p=vp.transform(new Vector4f(0,0,-distance,1));return p.z/p.w*.5f+.5f;}
        void inputs() {
            GlState.fullscreen();fixture.use();fixture.scalar("NearDepth",depth(5));fixture.scalar("FarDepth",cosmicScene || constantDepth?1:depth(100));fixture.scalar("Surface",cosmicScene || constantDepth?1:depth(14));fixture.integer("Shape",shape);
            opaque.bind();fixture.integer("Mode",0);draw();surface.bind();fixture.integer("Mode",1);draw();if(cosmicScene)glClearBufferfv(GL_COLOR,0,new float[]{1,1,1,1});scene.bind();fixture.integer("Mode",cosmicScene || constantDepth?0:2);draw();
        }
        void bounds() {
            BlackHoleScreenBounds b=new BlackHoleScreenBounds();
            check(b.project(vp,new Vector4f(0,0,0,1),centerX,0,-distance,finaleTicks<0?15.1:Math.max(32,waves.support),width,height),"visible fixture");
            rect.normalized(b.left/(float)width-(legacy?0:2f)/solution.width,b.bottom/(float)height-(legacy?0:2f)/solution.height,b.right/(float)width+(legacy?0:2f)/solution.width,b.top/(float)height+(legacy?0:2f)/solution.height,solution.width,solution.height);
            int pad=glowStrength>0?GlBloom.padding(steps):0;glow.set(Math.max(0,b.left-pad),Math.max(0,b.bottom-pad),Math.min(width,b.right+pad),Math.min(height,b.top+pad));
        }
        void material(GlProgram p,boolean solving) {
            if(finaleTicks>=0) {
                var frame=timeline.frame(finaleTicks);
                FinaleWaves.evaluate(timeline,finaleTicks,3.2,11,.12,waves);FinaleWaveUniforms.apply(p,waves);
                CausalTransition.evaluate(timeline,finaleTicks,-1,intro);CausalUniforms.apply(p,intro);
                p.scalar("FinaleAmount",frame.phase()==FinaleTimeline.Phase.EXPOSE?CausalTransition.reveal(frame.progress()):1);
                p.integer("FinalePhase",frame.phase().ordinal());p.scalar("FinaleProgress",frame.progress());p.scalar("FinaleTime",finaleTicks/20);
                p.scalar("FinalePulse",timeline.exposure(finaleTicks,-1).pulse());
                p.scalar("FinaleOrbit",FinaleChoreography.motion(timeline,finaleTicks).angle());
            } else {p.scalar("FinaleAmount",0);p.integer("WavePresent",0);p.integer("IntroEnabled",0);}
            p.integer("SkipFieldLens",legacy?0:1);p.pair("SceneSize",width,height);p.matrix("VP",vp);p.matrix("InverseVP",inv);p.vector("Eye",0,0,0);p.vector("Center",centerX,0,-distance);
            p.vector("DiskNormal",0,.8660254f,.5f);p.vector("DiskRight",1,0,0);p.vector("DiskForward",0,-.5f,.8660254f);
            p.vector("HotColor",1,.976f,.95f);p.vector("ColdColor",.596f,.463f,.365f);
            p.scalar("Radius",3.2);p.scalar("Inner",3.8);p.scalar("Outer",11);p.scalar("Thickness",.12);p.scalar("Brightness",3.2);p.scalar("Rotation",.25);p.scalar("Infall",.12);p.scalar("Lensing",1);p.scalar("Glow",glowStrength);p.scalar("Time",time);p.scalar("Seed",12);p.integer("Steps",steps);
            p.scalar("PixelAngle",2./(height*vp.m11()*(solving?scale:1)));
            p.texture("OpaqueDepth",8,constantDepth?constant.textures[1]:opaque.textures[1]);p.texture("SurfaceDepth",9,constantDepth?constant.textures[1]:surface.textures[1]);p.texture("DiskTexture",10,disk.handle());
            p.integer("SolutionBounded",legacy || rect.full(solution.width,solution.height)?0:1);rect.upload(p,"SolutionBounds");
            if(!legacy){advection.evaluate(time,.25,.12,7.2,12);advection.apply(p);p.integer("DiskRenewal",renew?1:0);}
        }
        void snapshot(GlTarget input,GlRect dirty) {
            snapshot.bind();if(dirty==null)glDisable(GL_SCISSOR_TEST);else dirty.scissor();copy.use();copy.texture("Source",0,input.textures[0]);copy.texture("Depth",1,opaque.textures[1]);copy.integer("Mode",0);draw();glDisable(GL_SCISSOR_TEST);
        }
        void solve() {
            solution.bind();if(legacy || rect.full(solution.width,solution.height))glDisable(GL_SCISSOR_TEST);else rect.scissor();
            glClearBufferfv(GL_COLOR,0,new float[]{0,0,0,1});glClearBufferfv(GL_COLOR,1,new float[]{0,0,0,1});glClearBufferfv(GL_COLOR,2,new float[]{0,0,0,0});glClearBufferfv(GL_COLOR,3,new float[]{1000000,1000000,0,-1});
            rect.scissor();solve.use();material(solve,true);draw();glDisable(GL_SCISSOR_TEST);
        }
        void bloom(int mode) {
            if(glowStrength<=0)return;GlProgram blur=!legacy&&rect.full(solution.width,solution.height)?blurFull:this.blur;blur.use();material(blur,false);blur.pair("FullSize",width,height);
            blur.texture("FrontTexture",0,solution.textures[0]);blur.texture("BackTexture",1,solution.textures[1]);blur.texture("Transmission",2,constantDepth?constant.textures[0]:surface.textures[0]);blur.texture("LensTexture",3,solution.textures[2]);
            if(legacy) oldBloom.render(blur,mode,vao,glow.left/(float)width,glow.bottom/(float)height,glow.right/(float)width,glow.top/(float)height);
            else bloom.render(blur,mode,vao,glow.left/(float)width,glow.bottom/(float)height,glow.right/(float)width,glow.top/(float)height);
        }
        void compose(GlTarget target,int mode) {
            GlProgram compose=!legacy&&rect.full(solution.width,solution.height)?composeFull:this.compose;
            target.bind();if(target==out){if(mode==1)glDrawBuffers(new int[]{GL_COLOR_ATTACHMENT0,GL_COLOR_ATTACHMENT1});else glDrawBuffer(GL_COLOR_ATTACHMENT0);}
            glow.scissor();compose.use();material(compose,false);
            compose.texture("Scene",0,snapshot.textures[0]);compose.texture("Opaque",1,cosmicScene&&!legacy?snapshot.textures[0]:opaque.textures[0]);compose.texture("Transmission",2,constantDepth?constant.textures[0]:surface.textures[0]);compose.texture("FrontTexture",3,solution.textures[0]);compose.texture("BackTexture",4,solution.textures[1]);compose.texture("LensTexture",5,solution.textures[2]);compose.texture("MetaTexture",6,solution.textures[3]);compose.texture("Bloom",7,(legacy?oldBloom.result():bloom.result()).textures[0]);bloom.bounds(compose);compose.integer("LayerMode",mode);compose.integer("DisplaySpace",0);draw();glDisable(GL_SCISSOR_TEST);
        }
        void cachedFront() {
            GlProgram frontShader=rect.full(solution.width,solution.height)?frontFull:this.frontShader;
            front.bind();glow.scissor();frontShader.use();material(frontShader,false);frontShader.integer("LayerMode",2);frontShader.texture("CachedSubject",3,out.textures[1]);frontShader.texture("Transmission",2,constantDepth?constant.textures[0]:surface.textures[0]);frontShader.texture("Bloom",7,(legacy?oldBloom.result():bloom.result()).textures[0]);bloom.bounds(frontShader);draw();glDisable(GL_SCISSOR_TEST);
        }
        void frame(int subjects) {
            GlState.fullscreen();
            if(cosmicScene) {
                if(legacy) {
                    glBindFramebuffer(GL_READ_FRAMEBUFFER,scene.framebuffer);glReadBuffer(GL_COLOR_ATTACHMENT0);
                    glBindFramebuffer(GL_DRAW_FRAMEBUFFER,opaque.framebuffer);glDrawBuffer(GL_COLOR_ATTACHMENT0);
                    glBlitFramebuffer(0,0,width,height,0,0,width,height,GL_COLOR_BUFFER_BIT,GL_NEAREST);
                    opaque.bind();glClearBufferfv(GL_COLOR,1,new float[]{1,0,0,0});
                    surface.bind();glClearBufferfv(GL_COLOR,0,new float[]{1,1,1,1});glClearBufferfv(GL_COLOR,1,new float[]{1,0,0,0});
                } else {constant.bind();glClearBufferfv(GL_COLOR,0,new float[]{1,1,1,1});glClearBufferfv(GL_COLOR,1,new float[]{1,0,0,0});}
            }
            if(finaleTicks>=0)FinaleWaves.evaluate(timeline,finaleTicks,3.2,11,.12,waves);
            glBindFramebuffer(GL_READ_FRAMEBUFFER,scene.framebuffer);glReadBuffer(GL_COLOR_ATTACHMENT0);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER,out.framebuffer);glDrawBuffer(GL_COLOR_ATTACHMENT0);
            glBlitFramebuffer(0,0,width,height,0,0,width,height,GL_COLOR_BUFFER_BIT,GL_NEAREST);
            snapshot(scene,null);float originalX=centerX;
            for(int i=0;i<subjects;i++) {
                if(i>0)snapshot(out,legacy||fullCopies?null:glow);
                centerX=originalX+(subjects==1?0:(i-1)*28);bounds();solve();bloom(split?2:1);compose(out,split?1:0);
                if(split){bloom(3);if(legacy)compose(front,2);else cachedFront();}
            }
            centerX=originalX;
        }
        void poison() {
            solution.bind();glDisable(GL_SCISSOR_TEST);for(int i=0;i<4;i++)glClearBufferfv(GL_COLOR,i,new float[]{200,200,200,0});
        }
        public void close(){glUseProgram(0);for(var t:new GlTarget[]{opaque,surface,scene,snapshot,solution,out,front,reference,constant})t.close();bloom.close();oldBloom.close();disk.close();for(var p:new GlProgram[]{solve,compose,blur,copy,frontShader,fixture,composeFull,blurFull,frontFull})if(p!=null)p.close();}
    }
    static float[] image(GlTarget t,int attachment){t.bind();glReadBuffer(GL_COLOR_ATTACHMENT0+attachment);var b=BufferUtils.createFloatBuffer(t.width*t.height*4);glReadPixels(0,0,t.width,t.height,GL_RGBA,GL_FLOAT,b);float[] a=new float[b.remaining()];b.get(a);return a;}
    static double difference(float[] a,float[] b,GlRect r,int width){double error=0;for(int y=r.bottom;y<r.top;y++)for(int x=r.left;x<r.right;x++)for(int c=0;c<4;c++){int i=(y*width+x)*4+c;check(Float.isFinite(a[i])&&Float.isFinite(b[i]),"finite pixel");error=Math.max(error,Math.abs(a[i]-b[i]));}return error;}
    private static double batch(Runnable frame) {
        int q=glGenQueries();glBeginQuery(GL_TIME_ELAPSED,q);
        for(int i=0;i<16;i++)frame.run();glEndQuery(GL_TIME_ELAPSED);
        double result=glGetQueryObjectui64(q,GL_QUERY_RESULT)/1e6/16;glDeleteQueries(q);return result;
    }
    private static double measure(Runnable frame) {
        for(int i=0;i<240;i++)frame.run();double[] batches=new double[9];
        for(int b=0;b<batches.length;b++)batches[b]=batch(frame);
        Arrays.sort(batches);return batches[4];
    }
    static double[] paired(Runnable baseline,Runnable current) {
        for(int i=0;i<240;i++){baseline.run();current.run();}
        double[] a=new double[11],b=new double[11];
        for(int i=0;i<a.length;i++) {
            if((i&1)==0){a[i]=batch(baseline);b[i]=batch(current);}
            else {b[i]=batch(current);a[i]=batch(baseline);}
        }
        Arrays.sort(a);Arrays.sort(b);return new double[]{a[5],b[5]};
    }
    private static void materialChecks(Pipeline p)throws Exception {
        String f="#version 330 core\nin vec2 texCoord;out vec4 Color;\n"+VerificationShaders.read(source,"field.glsl")+"\nvoid main(){vec2 q=vec2(cos(texCoord.x*6.2831853),sin(texCoord.x*6.2831853))*mix(3.8,11.,texCoord.y);Color=vec4(diskMaterial(q,.001),1);}";
        try(var program=new GlProgram(VerificationShaders.read(source,"fullscreen.vsh"),f);var result=new GlTarget(GL_RGBA32F)){
            result.resize(256,64);double[] times={0,60,600,3600,1048575.999,1048576.001};
            for(double t:times){p.time=t;program.use();p.material(program,false);result.bind();draw();float[] a=image(result,0);double sum=0,sq=0;for(int i=1;i<a.length;i+=4){check(Float.isFinite(a[i]),"finite long-time material");sum+=a[i];sq+=a[i]*a[i];}int n=a.length/4;double contrast=Math.sqrt(sq/n-Math.pow(sum/n,2));check(contrast>.04,"long-time material retains detail t="+t);System.out.printf(Locale.ROOT,"material t=%.3f contrast=%.5f%n",t,contrast);}
            for(double t:new double[]{0,4,8,60,3600,1048576}){float[][] samples=new float[2][];for(int k=0;k<2;k++){p.time=t+(k==0?-1e-5:1e-5);program.use();p.material(program,false);result.bind();draw();samples[k]=image(result,0);}GlRect rect=new GlRect();rect.set(0,0,256,64);check(difference(samples[0],samples[1],rect,256)<.005,"cohort boundary continuity "+t);}
        }p.time=7;
    }
    static void resourceChecks(Pipeline p) {
        glUseProgram(0);check(glGetError()==GL_NO_ERROR,"resource test starts without stale errors");
        try(var host=new GlTarget(GL_RGBA16F)) {
            host.resize(32,24);host.bind();glViewport(2,3,19,17);glEnable(GL_SCISSOR_TEST);glScissor(1,2,13,11);
            glEnable(GL_BLEND);glBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ZERO);
            glActiveTexture(GL_TEXTURE12);glBindTexture(GL_TEXTURE_2D,host.textures[0]);
            try(var state=GlState.capture()) {
                p.resize(800,450);p.split=true;p.frame(1);
                int fbo=glGenFramebuffers();glBindFramebuffer(GL_FRAMEBUFFER,fbo);
                glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,p.out.textures[0],0);
                try {
                    glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT1,GL_TEXTURE_2D,p.front.textures[0],0);
                    glDrawBuffers(new int[]{GL_COLOR_ATTACHMENT0,GL_COLOR_ATTACHMENT1});
                    check(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE,"temporary split MRT complete");
                    throw new IllegalStateException("injected resolve failure");
                } catch(IllegalStateException expected) {
                    check(expected.getMessage().equals("injected resolve failure"),"resolve failure fixture");
                } finally {
                    glDrawBuffer(GL_COLOR_ATTACHMENT0);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT1,GL_TEXTURE_2D,0,0);
                    check(glGetFramebufferAttachmentParameteri(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT1,GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)==GL_NONE,"temporary front attachment detached on failure");
                    glDeleteFramebuffers(fbo);
                }
            }
            check(glGetError()==GL_NO_ERROR,"split MRT and state restore GL checks");
            check(glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)==host.framebuffer && glGetInteger(GL_READ_FRAMEBUFFER_BINDING)==host.framebuffer,"restore host framebuffers");
            int[] viewport=new int[4],scissor=new int[4];glGetIntegerv(GL_VIEWPORT,viewport);glGetIntegerv(GL_SCISSOR_BOX,scissor);
            check(Arrays.equals(viewport,new int[]{2,3,19,17}) && Arrays.equals(scissor,new int[]{1,2,13,11}),"restore viewport and scissor");
            check(glIsEnabled(GL_SCISSOR_TEST)&&glIsEnabled(GL_BLEND)&&glGetInteger(GL_BLEND_SRC_RGB)==GL_SRC_ALPHA,"restore host blend enables");
            check(glGetInteger(GL_ACTIVE_TEXTURE)==GL_TEXTURE12&&glGetInteger(GL_TEXTURE_BINDING_2D)==host.textures[0],"restore host texture binding");
            GlState.fullscreen();glBindFramebuffer(GL_FRAMEBUFFER,0);glBindTexture(GL_TEXTURE_2D,0);
        }
        try(var target=new GlTarget(GL_RGBA16F,GL_R32F)) {
            target.resize(100,80);int texture=target.textures[0],fbo=target.framebuffer;
            check(target.bytes()==100L*80*12,"target byte estimate");target.close();
            check(!glIsTexture(texture)&&!glIsFramebuffer(fbo)&&target.bytes()==0,"target release clears ownership");
            target.resize(64,48);check(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE,"released target recreates");
        }
        check(glGetError()==GL_NO_ERROR,"resource and failure GL checks");
        p.resize(640,360);
    }
    private static void mathChecks() {
        DiskAdvection a=new DiskAdvection(),b=new DiskAdvection();
        for(int i=0;i<20000;i++) {
            double t=i*.113+((i&1)==0?1048570:0);a.evaluate(t,.25,.12,7.2,17);b.evaluate(t,.25,.12,7.2,17);
            check(Arrays.equals(a.age,b.age)&&Arrays.equals(a.anchor,b.anchor)&&a.flow==b.flow&&a.blend==b.blend,"pause/late join determinism");
            check(a.blend>=0&&a.blend<=1&&a.flow>=0&&a.flow<1,"finite phase range");
            for(int k=0;k<2;k++)check(Math.abs(a.age[k])<=4&&a.anchor[k]>=0&&a.anchor[k]<1,"bounded shear age");
            double complementary=Math.cos(Math.PI*a.age[1]/8);check(Math.abs(a.blend+complementary*complementary-1)<1e-8,"complementary cohort energy");
        }
        a.evaluate(0,0,0,7.2,17);b.evaluate(1e8,0,0,7.2,17);check(Arrays.equals(a.age,b.age)&&Arrays.equals(a.offset,b.offset)&&a.flow==b.flow,"static material does not renew");
    }
    private static long classify(Pipeline p,GlProgram program,int mode) {
        p.out.bind();p.glow.scissor();program.use();p.material(program,false);program.integer("FallbackOnly",mode);
        program.texture("FrontTexture",3,p.solution.textures[0]);program.texture("BackTexture",4,p.solution.textures[1]);program.texture("MetaTexture",6,p.solution.textures[3]);
        int query=glGenQueries();glColorMask(false,false,false,false);glBeginQuery(GL_SAMPLES_PASSED,query);draw();glEndQuery(GL_SAMPLES_PASSED);
        long result=glGetQueryObjectui64(query,GL_QUERY_RESULT);glDeleteQueries(query);glColorMask(true,true,true,true);glDisable(GL_SCISSOR_TEST);return result;
    }
    public static void main(String[] args)throws Exception {
        mathChecks();source=Path.of(args[0]);before=Path.of(args[1]);
        check(glfwInit(),"GLFW");glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        long window=glfwCreateWindow(64,64,"renderer verification",0,0);check(window!=0,"context");glfwMakeContextCurrent(window);GL.createCapabilities();vao=glGenVertexArrays();
        System.out.println("GPU: "+glGetString(GL_RENDERER));
        try(var p=new Pipeline(false);var old=new Pipeline(true)){
            try(var count=shader(source,"reconstruction_count")){check(count.handle()!=0,"classifier links");}
            for(int shape=0;shape<4;shape++){
                p.shape=shape;p.inputs();p.frame(1);float[] cached=image(p.front,0);p.compose(p.reference,2);check(difference(cached,image(p.reference,0),p.glow,p.width)<.003,"cached front equals direct resolve shape="+shape);
            }
            p.shape=0;p.distance=120;p.glowStrength=.35f;p.inputs();p.frame(1);float[] clean=image(p.out,0);p.poison();p.frame(1);check(difference(clean,image(p.out,0),p.glow,p.width)<.001,"bounded solution rejects stale data");
            p.constantDepth=true;p.inputs();p.frame(1);float[] constant=image(p.out,0);p.constantDepth=false;
            p.opaque.bind();glClearBufferfv(GL_COLOR,1,new float[]{1,0,0,0});p.surface.bind();glClearBufferfv(GL_COLOR,0,new float[]{1,1,1,1});glClearBufferfv(GL_COLOR,1,new float[]{1,0,0,0});p.frame(1);check(difference(constant,image(p.out,0),p.glow,p.width)<.003,"1x1 cosmic depth uses full-resolution scene footprint");
            p.constantDepth=false;p.inputs();p.distance=35;p.frame(3);float[] incremental=image(p.out,0);
            p.fullCopies=true;p.frame(3);GlRect whole=new GlRect();whole.set(0,0,p.width,p.height);
            check(difference(incremental,image(p.out,0),whole,p.width)<.001,"incremental snapshots equal complete copies for separated subjects");p.fullCopies=false;
            try(var classifier=shader(source,"reconstruction_count")) {
                p.shape=3;p.inputs();p.bounds();check(classify(p,classifier,0)==0,"fully occluded classifier has no eligible pixels");
                p.shape=0;p.inputs();p.bounds();p.solve();long eligible=classify(p,classifier,0);check(eligible>0,"classifier sees visible field");
                p.solution.bind();glClearBufferfv(GL_COLOR,3,new float[]{1000000,1000000,0,-1});
                check(classify(p,classifier,1)==eligible,"classifier counts exact reconstruction fallback");
            }
            materialChecks(p);resourceChecks(p);
            if(args.length>2 && args[2].equals("checks")) {System.out.println("PASS "+checks+" stability/resource checks");return;}
            if(args.length>2 && args[2].equals("qualities")) {
                for(int steps:new int[]{24,96}) {
                    p.steps=old.steps=steps;p.scale=old.scale=steps==24?.375f:.75f;
                    p.resize(1920,1080);old.resize(1920,1080);p.distance=old.distance=22;
                    for(boolean split:new boolean[]{true,false}) {
                        p.split=old.split=split;p.shape=old.shape=1;p.inputs();old.inputs();
                        double[] pair=paired(()->old.frame(1),()->p.frame(1));
                        System.out.printf(Locale.ROOT,"QUALITY steps=%d split=%s old=%.3f new=%.3f delta=%+.3f budget=%.3f ms%n",steps,split,pair[0],pair[1],pair[1]-pair[0],Math.max(.1,pair[0]*.05));
                        check(pair[1]-pair[0]<=Math.max(.1,pair[0]*.05),"quality GPU budget "+steps+" split="+split);
                    }
                }
                p.steps=48;p.scale=.5f;p.resize(1920,1080);p.shape=0;p.inputs();
                double[] materialPair=paired(()->{p.renew=false;p.frame(1);},()->{p.renew=true;p.frame(1);});
                System.out.printf(Locale.ROOT,"DUAL MATERIAL paired incremental %+.3f ms (single %.3f / dual %.3f)%n",materialPair[1]-materialPair[0],materialPair[0],materialPair[1]);
                System.out.println("PASS low/high quality budgets");return;
            }
            if(args.length>2 && args[2].equals("peaks")) {
                p.resize(1920,1080);old.resize(1920,1080);p.distance=old.distance=22;p.split=old.split=false;p.shape=old.shape=0;
                for(double seconds:new double[]{4.35,4.475,40.4,40.95,44,88,100}) {
                    p.finaleTicks=old.finaleTicks=seconds*20;p.cosmicScene=old.cosmicScene=seconds>=40.9;
                    p.constantDepth=p.cosmicScene;old.constantDepth=false;p.inputs();old.inputs();
                    double[] pair=paired(()->old.frame(1),()->p.frame(1));
                    System.out.printf(Locale.ROOT,"PEAK subject+inputs t=%.3f cosmic=%s old=%.3f new=%.3f delta=%+.3f budget=%.3f ms%n",seconds,p.cosmicScene,pair[0],pair[1],pair[1]-pair[0],Math.max(.1,pair[0]*.05));
                    check(pair[1]-pair[0]<=Math.max(.1,pair[0]*.05),"peak GPU budget t="+seconds);
                }
                check(glGetError()==GL_NO_ERROR,"peak GL state");System.out.println("PASS peak budgets (subject and input stages; excludes unchanged sky/glare/late compositor)");return;
            }
            if(args.length>2) {
                p.resize(1920,1080);old.resize(1920,1080);p.distance=old.distance=22;p.shape=old.shape=1;p.split=old.split=false;p.inputs();old.inputs();
                System.out.println("ABLATE baseline="+measure(()->old.frame(1)));
                p.renew=false;System.out.println("ABLATE new old-material="+measure(()->p.frame(1)));
                p.renew=true;System.out.println("ABLATE new dual-material="+measure(()->p.frame(1)));
                System.out.println("SOLVE old="+measure(old::solve)+" new="+measure(p::solve));
                System.out.println("BLOOM old="+measure(()->old.bloom(1))+" new="+measure(()->p.bloom(1)));
                System.out.println("COMPOSE old="+measure(()->old.compose(old.out,0))+" new="+measure(()->p.compose(p.out,0)));return;
            }
            for(int[] size:new int[][]{{1920,1080},{2560,1440},{3840,2160}}){
                p.resize(size[0],size[1]);old.resize(size[0],size[1]);
                for(int scenario=0;scenario<7;scenario++){
                    int bodies=scenario==3?3:1;float distance=scenario==0 || scenario==4?120:22;int shape=scenario==2 || scenario==6?1:0;
                    p.split=old.split=scenario<4;
                    p.distance=old.distance=distance;p.shape=old.shape=shape;p.inputs();old.inputs();p.time=old.time=7;
                    double[] pair=paired(()->old.frame(bodies),()->p.frame(bodies));double baseline=pair[0],after=pair[1];
                    System.out.printf(Locale.ROOT,"BENCH %dx%d scenario=%d subjects=%d old=%.3f new=%.3f delta=%+.3f budget=%.3f ms%n",size[0],size[1],scenario,bodies,baseline,after,after-baseline,Math.max(.1,baseline*.05));
                    check(after-baseline<=Math.max(.1,baseline*.05),"stable GPU budget "+size[0]+" scenario="+scenario);
                }
            }
            p.resize(1920,1080);p.distance=22;p.shape=0;p.split=false;p.inputs();double[] materialPair=paired(()->{p.renew=false;p.frame(1);},()->{p.renew=true;p.frame(1);});double singleMaterial=materialPair[0],dualMaterial=materialPair[1];p.renew=true;System.out.printf(Locale.ROOT,"DUAL MATERIAL incremental %.3f ms (single %.3f / dual %.3f)%n",dualMaterial-singleMaterial,singleMaterial,dualMaterial);
            p.resize(640,360);p.distance=35;p.shape=0;p.inputs();
            for(int steps:new int[]{24,48,96}){p.steps=steps;p.scale=steps==24?.375f:steps==48?.5f:.75f;p.resize(640,360);p.frame(1);check(glGetError()==GL_NO_ERROR,"quality switch "+steps);}
            try(var diagnostic=new GpuDiagnostics()) {for(int i=0;i<140;i++){diagnostic.frame(true,true,"verification",640,360);diagnostic.start("parent");diagnostic.start("child");draw();diagnostic.end("child");diagnostic.end("parent");}diagnostic.frame(false,true,"disabled",640,360);check(glGetError()==GL_NO_ERROR,"nonblocking diagnostic lifecycle");}
            check(glGetError()==GL_NO_ERROR,"no GL errors");
        }finally{glUseProgram(0);glDeleteVertexArrays(vao);glfwDestroyWindow(window);glfwTerminate();}
        System.out.println("PASS "+checks+" checks. Synthetic GL, not Minecraft/Oculus/EndingLibrary runtime certification.");
    }
}
