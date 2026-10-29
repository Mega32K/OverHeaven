package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static com.mega.overheaven.foliagecompat.blackhole.client.FinaleGpuVerification.*;
import com.mega.overheaven.foliagecompat.finale.FinaleTimeline;
import java.nio.file.*;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL;

public final class FinaleFlashGpuVerification {
    private static final FinaleTimeline TIMELINE=FinaleTimeline.defaults();
    private static final StringBuilder REPORT=new StringBuilder();
    private static void expect(boolean passed,String message) { check(passed,message); REPORT.append("PASS ").append(message).append('\n'); }
    private static double difference(float[] first,float[] second) {
        double result=0;
        for(int offset=0;offset<first.length;offset+=4) for(int channel=0;channel<3;channel++) result+=Math.abs(first[offset+channel]-second[offset+channel]);
        return result;
    }
    private static double energy(float[] data) { double value=0; for(int offset=0;offset<data.length;offset+=4) value+=data[offset]+data[offset+1]+data[offset+2]; return value; }
    private static boolean solid(float[] data,float expected) {
        for(int offset=0;offset<data.length;offset+=4) for(int channel=0;channel<3;channel++) if(data[offset+channel]!=expected) return false;
        return true;
    }
    public static void main(String[] args) throws Exception {
        source=Path.of(args[0]); output=Path.of(args[1]); Files.createDirectories(output);
        expect(glfwInit(),"GLFW initialized"); glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE); glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
        long window=glfwCreateWindow(64,64,"finale layers",0,0); expect(window!=0,"hidden GL context"); glfwMakeContextCurrent(window); GL.createCapabilities(); vao=glGenVertexArrays();
        REPORT.append("GPU: ").append(glGetString(GL_RENDERER)).append("; 1920x1080 synthetic framebuffer, production GLSL and bloom, no Minecraft/host execution\n");
        try(var pipeline=new Pipeline()) {
            pipeline.resize(48,.5f); GlState.fullscreen();
            pipeline.world.bind(); glClearBufferfv(GL_COLOR,0,new float[]{.08f,.11f,.17f,1});
            var view=new Matrix4f(); pipeline.cosmos(824,view,0,-24,true);
            float[] subjects=pixels(pipeline.cosmos),background=pixels(pipeline.background);
            expect(difference(subjects,background)>10,"star background preserves singularity membranes, disk and companion black hole");
            image(pipeline.cosmos,"cosmos-subjects");
            for(double seconds:new double[]{-.01,0,.25,.5,.6,.7,.8}) {
                double ticks=808+seconds*20;
                pipeline.cosmos.bind(); glClearBufferfv(GL_COLOR,0,new float[]{.08f,.11f,.17f,1}); pipeline.depth(1);
                pipeline.subject(ticks,view,8,-32,false); pipeline.subject(ticks,view,0,-24,true); pipeline.blit(pipeline.cosmos,pipeline.world,GL_NEAREST);
                pipeline.cosmos(ticks,view,0,-24,true); pipeline.compose(ticks,-1);
                var envelope=TIMELINE.exposure(ticks,-1); float[] actual=pixels(pipeline.result);
                expect(Arrays.stream(new double[]{envelope.white(),envelope.coverage()}).allMatch(value->value>=0 && value<=1),"bounded envelope at "+seconds);
                if(seconds==.5 || seconds==.6) expect(solid(actual,1),"every pixel is exactly white at "+seconds);
                if(seconds==.8) expect(difference(actual,pixels(pipeline.cosmos))<.1,"zero exposure residue at 0.8s");
                if(seconds<0) expect(difference(actual,pixels(pipeline.world))<.1,"world unchanged before trigger");
                image(pipeline.result,String.format(Locale.ROOT,"flash-%+.2fs",seconds));
            }
            pipeline.world.bind(); glClearBufferfv(GL_COLOR,0,new float[]{.25f,.25f,.25f,1});
            pipeline.cosmos.bind(); glClearBufferfv(GL_COLOR,0,new float[]{.75f,.75f,.75f,1});
            for(double seconds:new double[]{.25,.7}) {
                double ticks=808+seconds*20; var envelope=TIMELINE.exposure(ticks,-1); pipeline.compose(ticks,-1);
                double base=envelope.coverage()==0?.25:.75;
                double expected=(base+(1-Math.exp(-base*2))*envelope.pulse()*.06)*(1-envelope.white())+envelope.white();
                float[] actual=pixels(pipeline.result);
                expect(Math.abs(actual[0]-expected)<.001,"GPU quartic exposure and soft tail at "+seconds);
            }
            pipeline.compose(822,.5); float[] stopped=pixels(pipeline.result);
            expect(stopped[0]>.25 && stopped[0]<.75,"stop blends complete images without adding subjects");
            pipeline.compose(822,1); expect(pixels(pipeline.result)[0]==.25f,"stop endpoint exactly restores world");
            pipeline.compose(2360,-1); expect(Math.abs(pixels(pipeline.result)[0]-.5f)<.001,"return midpoint is weighted images, not additive subjects");
            pipeline.compose(2288,-1); expect(solid(pixels(pipeline.result),0),"late exact black overrides all layers");
            // Final wave still refracts the background at tick 824, even behind the camera.
            // Test fake offscreen subjects after that wave has finished.
            pipeline.cosmos(900,new Matrix4f().rotateY((float)Math.PI),0,-24,false);
            expect(difference(pixels(pipeline.cosmos),pixels(pipeline.background))<.1,"backward view has no clamped or fake singularity");
            pipeline.compose(818,-1); expect(solid(pixels(pipeline.result),1),"backward view still flashes full white");
            pipeline.cosmos(900,new Matrix4f().rotateX(-1.55f),0,-24,false);
            expect(difference(pixels(pipeline.cosmos),pixels(pipeline.background))<.1,"upward view has no edge-clamped singularity");
            image(pipeline.cosmos,"upward-background");
            pipeline.cosmos(824,new Matrix4f(),0,-1,false);
            expect(difference(pixels(pipeline.cosmos),pixels(pipeline.background))>1,"camera inside subject bounds still renders membranes"); image(pipeline.cosmos,"inside-subject");
            pipeline.cosmos(824,view,0,-24,false); float[] stable=pixels(pipeline.cosmos);
            pipeline.cosmos(824,view,0,-24,false); expect(Arrays.equals(stable,pixels(pipeline.cosmos)),"frozen timeline produces identical pixels");
            // Isolate source occlusion: the earlier wave can legitimately be in front of this wall.
            pipeline.depth(.9f); pipeline.subject(900,view,0,-24,true); expect(energy(pixels(pipeline.solution))==0,"opaque world-depth input blocks source-only singularity radiation");
            pipeline.cosmos(824,view,0,-24,false); expect(difference(stable,pixels(pipeline.cosmos))<.1,"cosmic input resets hidden terrain depth");
            pipeline.cosmos.bind(); glClearBufferfv(GL_COLOR,0,new float[]{.25f,.25f,.25f,1}); pipeline.depth(1); pipeline.subject(824,view,0,-24,false);
            float[] ordinary=pixels(pipeline.cosmos); int center=((H/2)*W+W/2)*4;
            expect(ordinary[center]<.05,"normal black core absorbs cosmic background");
            image(pipeline.cosmos,"ordinary-core-disk-bloom");
            pipeline.cosmos(2208,view,0,-24,false); expect(energy(pixels(pipeline.background))==0 && energy(pixels(pipeline.cosmos))>0,"silence remnant belongs to subject, not stars"); image(pipeline.cosmos,"silence-subject-remnant");
            for(int steps:new int[]{24,48,96}) {
                pipeline.resize(steps,steps==24?.375f:steps==96?.75f:.5f); pipeline.cosmos(824,view,0,-24,true);
                expect(difference(pixels(pipeline.cosmos),pixels(pipeline.background))>10,"quality switch keeps complete subjects "+steps);
            }
            pipeline.resize(48,.5f);
            pipeline.result.bind(); glViewport(3,5,811,607); glActiveTexture(GL_TEXTURE5); glEnable(GL_BLEND); glEnable(GL_DEPTH_TEST); glDepthMask(true);
            pipeline.cosmos(824,view,0,-24,true);
            int[] viewport=new int[4]; glGetIntegerv(GL_VIEWPORT,viewport);
            expect(glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)==pipeline.result.framebuffer && Arrays.equals(viewport,new int[]{3,5,811,607}) && glGetInteger(GL_ACTIVE_TEXTURE)==GL_TEXTURE5 && glIsEnabled(GL_BLEND) && glIsEnabled(GL_DEPTH_TEST) && glGetBoolean(GL_DEPTH_WRITEMASK),"FBO viewport textures blend depth restored");
            GlState.fullscreen();
            for(double ticks:new double[]{3,340,813,824,1100,1452,1900,2208,2288,2360}) {
                int query=glGenQueries();
                for(int frame=0;frame<20;frame++) {
                    if(frame==4) glBeginQuery(GL_TIME_ELAPSED,query);
                    pipeline.cosmos(ticks,view,0,-24,false); pipeline.compose(ticks,-1);
                }
                glEndQuery(GL_TIME_ELAPSED); double millis=glGetQueryObjectui64(query,GL_QUERY_RESULT)/16e6; glDeleteQueries(query);
                REPORT.append(String.format(Locale.ROOT,"fixture phase=%s ticks=%.1f stars+subject+bloom+late %.3f ms/frame%n",TIMELINE.frame(ticks).phase(),ticks,millis));
            }
            Path before=output.getParent().resolve("before/src/main/resources/assets/overheaven_foliage_compat/shaders/blackhole");
            if(!Files.exists(before.resolve("fullscreen.vsh"))) REPORT.append("Full-composition baseline timing not run: partial snapshot; paired star-only timing is recorded separately.\n");
            else try(var original=new Pipeline(before,null)) {
                original.resize(48,.5f); original.world.bind(); glClearBufferfv(GL_COLOR,0,new float[]{.08f,.11f,.17f,1});
                double[] costs=new double[2];
                for(int variant=0;variant<2;variant++) {
                    Pipeline selected=variant==0?original:pipeline; int query=glGenQueries();
                    for(int frame=0;frame<44;frame++) {
                        if(frame==8) glBeginQuery(GL_TIME_ELAPSED,query);
                        selected.cosmos(1900,view,0,-24,false); selected.compose(1900,-1);
                    }
                    glEndQuery(GL_TIME_ELAPSED); costs[variant]=glGetQueryObjectui64(query,GL_QUERY_RESULT)/36e6; glDeleteQueries(query);
                    image(selected.result,variant==0?"before-extinguish":"after-extinguish");
                }
                REPORT.append(String.format(Locale.ROOT,"same-context full-composition baseline %.3f ms / new %.3f ms / delta %+.3f ms%n",costs[0],costs[1],costs[1]-costs[0]));
            }
            expect(glGetError()==GL_NO_ERROR,"GL error-free after layers and quality switches");
            int texture=pipeline.cosmos.textures[0],framebuffer=pipeline.cosmos.framebuffer; pipeline.cosmos.close();
            expect(!glIsTexture(texture) && !glIsFramebuffer(framebuffer),"cosmic target releases owned GPU resources");
            pipeline.cosmos.resize(W,H); expect(glIsTexture(pipeline.cosmos.textures[0]),"target recreates after reload");
            REPORT.append("Fixture tests actual shaders and inputs, not Minecraft render hooks; timings include synthetic stars even for pre-takeover phases and exclude host effects.\n");
        } finally { glDeleteVertexArrays(vao); glfwDestroyWindow(window); glfwTerminate(); }
        Files.writeString(output.resolve("report.txt"),REPORT); System.out.println(REPORT);
    }
    static final class Pipeline implements AutoCloseable {
        final GlProgram stars,integrate,composite,bloom,late,copy;
        final GlTarget world=new GlTarget(GL_RGBA16F),background=new GlTarget(GL_RGBA16F),cosmos=new GlTarget(GL_RGBA16F),scene=new GlTarget(GL_RGBA16F),result=new GlTarget(GL_RGBA16F);
        final GlTarget starLow=new GlTarget(GL_RGBA16F).linear(),opaque=new GlTarget(GL_RGBA16F,GL_R32F),surface=new GlTarget(GL_RGBA16F,GL_R32F),solution=new GlTarget(GL_RGBA16F,GL_RGBA16F,GL_RGBA32F,GL_RGBA32F);
        final GlBloom pyramid=new GlBloom(); final GlDiskTexture disk;
        final GlSingularityGlare glare;
        final Matrix4f projection=new Matrix4f().perspective((float)Math.toRadians(70),W/(float)H,.05f,4096);
        final BlackHoleScreenBounds bounds=new BlackHoleScreenBounds();
        int steps=48;
        int sceneOpaqueOverride,layerMode,displaySpace=1;
        boolean glareEnabled=true,impactEnabled=true;
        boolean spectrumEnabled=true;
        final boolean programHasWaves;
        final boolean programHasIntro;
        final FinaleStarMap nasa;
        boolean nasaEnabled=true;
        Pipeline() throws Exception { this(source,null); }
        Pipeline(Path resources,String starSource) throws Exception {
            nasa=starSource==null && Files.exists(resources.resolve("finale_nasa.fsh"))?new FinaleStarMap(name->Files.newInputStream(resources.resolve("../../textures/finale/"+name)),name->{try{return VerificationShaders.read(resources,name);}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}):null;
            if(nasa!=null) { nasa.prepare();while(nasa.loading()) Thread.sleep(5); }
            String vertex=VerificationShaders.read(resources,"fullscreen.vsh");
            programHasWaves=VerificationShaders.read(resources,"finale_energy.glsl").contains("WavePresent");
            programHasIntro=Files.readString(resources.resolve("field.glsl")).contains("uniform int IntroEnabled;");
            stars=new GlProgram(vertex,starSource==null?VerificationShaders.read(resources,"finale_stars.fsh"):starSource);
            integrate=new GlProgram(vertex,VerificationShaders.read(resources,"integrate.fsh"));
            String compositeSource=VerificationShaders.read(resources,"composite.fsh");
            compositeSource=compositeSource.replace("vec4 energySpectrum(vec3 direction,float opaque) {",
                "uniform int TestSpectrumDisabled;\nvec4 energySpectrum(vec3 direction,float opaque) {\nif(TestSpectrumDisabled!=0)return vec4(0,0,0,-1);");
            composite=new GlProgram(vertex,compositeSource);
            bloom=new GlProgram(vertex,VerificationShaders.read(resources,"bloom.fsh")); late=new GlProgram(vertex,VerificationShaders.read(resources,"finale_composite.fsh")); copy=new GlProgram(vertex,VerificationShaders.read(resources,"copy.fsh"));
            glare=Files.exists(resources.resolve("singularity_source.fsh"))?new GlSingularityGlare(vertex,VerificationShaders.read(resources,"singularity_source.fsh")):null;
            try(var stream=Files.newInputStream(resources.resolve("../../textures/blackhole/disk_detail.png"))) { disk=new GlDiskTexture(stream); }
        }
        void resize(int nextSteps,float scale) {
            steps=nextSteps; for(var target:new GlTarget[]{world,background,cosmos,scene,result,opaque,surface}) target.resize(W,H);
            starLow.resize((int)(W*scale),(int)(H*scale)); solution.resize((int)(W*scale),(int)(H*scale)); pyramid.resize(W,H,steps);
        }
        void blit(GlTarget from,GlTarget to,int filter) {
            glBindFramebuffer(GL_READ_FRAMEBUFFER,from.framebuffer); glBindFramebuffer(GL_DRAW_FRAMEBUFFER,to.framebuffer); glBlitFramebuffer(0,0,from.width,from.height,0,0,to.width,to.height,GL_COLOR_BUFFER_BIT,filter);
        }
        void depth(float value) {
            opaque.bind(); glClearBufferfv(GL_COLOR,1,new float[]{value,0,0,0}); surface.bind(); glClearBufferfv(GL_COLOR,0,new float[]{1,1,1,1}); glClearBufferfv(GL_COLOR,1,new float[]{1,0,0,0});
        }
        void cosmos(double ticks,Matrix4f view,float centerX,float centerZ,boolean companion) {
            try(var state=GlState.capture()) {
                GlState.fullscreen(); var frame=TIMELINE.frame(ticks);
                starLow.bind();
                boolean useNasa=nasaEnabled && nasa!=null;
                if(useNasa) nasa.drawBackground(p->uniforms(p,frame.phase().ordinal(),(float)frame.progress(),ticks,view),vao);
                else { stars.use(); uniforms(stars,frame.phase().ordinal(),(float)frame.progress(),ticks,view); draw(); }
                blit(starLow,cosmos,GL_LINEAR);
                if(useNasa) { cosmos.bind(); nasa.drawStars(p->uniforms(p,frame.phase().ordinal(),(float)frame.progress(),ticks,view),W,H,steps==24?0:steps==96?2:1); }
                blit(cosmos,background,GL_NEAREST); depth(1);
                if(!FinaleTimeline.black(frame) && frame.phase()!=FinaleTimeline.Phase.RETURN) {
                    if(companion) subject(ticks,view,8,-32,false);
                    subject(ticks,view,centerX,centerZ,true);
                }
            }
        }
        void material(GlProgram program,double ticks,Matrix4f view,float centerX,float centerZ,boolean sourceHole,int height) {
            var frame=TIMELINE.frame(ticks); nearMaterial(program,opaque,surface,disk,frame.phase().ordinal(),(float)frame.progress(),height);
            var matrix=new Matrix4f(projection).mul(view); var inverse=new Matrix4f(matrix).invert(); var eye=inverse.transform(new Vector4f(0,0,1,0)); eye.div(eye.w);
            program.matrix("VP",matrix); program.matrix("InverseVP",inverse); program.vector("Eye",eye.x,eye.y,eye.z); program.vector("Center",centerX,0,centerZ);
            double amount=sourceHole?1:0;
            if(sourceHole && frame.phase()==FinaleTimeline.Phase.EXPOSE) amount=(programHasIntro?com.mega.overheaven.foliagecompat.finale.CausalTransition.reveal(frame.progress()):FinaleTimeline.smooth(0,.01875,frame.progress()));
            if(sourceHole && frame.phase()==FinaleTimeline.Phase.RETURN) amount=1-FinaleTimeline.smooth(0,1,frame.progress());
            if(programHasWaves) {
                var wave=new com.mega.overheaven.foliagecompat.finale.FinaleWaves.State();
                if(sourceHole) com.mega.overheaven.foliagecompat.finale.FinaleWaves.evaluate(TIMELINE,ticks,3.2,11,.16,wave);
                FinaleWaveUniforms.apply(program,wave); program.integer("TestSpectrumDisabled",spectrumEnabled?0:1);
            }
            var intro=new com.mega.overheaven.foliagecompat.finale.CausalTransition.State();
            if(sourceHole) com.mega.overheaven.foliagecompat.finale.CausalTransition.evaluate(TIMELINE,ticks,-1,intro);
            CausalUniforms.apply(program,intro);
            program.scalar("GlarePulse",sourceHole?com.mega.overheaven.foliagecompat.finale.FinaleWaves.flare(TIMELINE,ticks):0);
            program.scalar("FinaleTime",ticks/20); program.scalar("Time",41.2); program.scalar("FinaleAmount",amount); program.scalar("FinalePulse",sourceHole?Math.sqrt(TIMELINE.flash(ticks)):0); program.integer("Steps",steps);
            program.scalar("FinaleOrbit",com.mega.overheaven.foliagecompat.finale.FinaleChoreography.motion(TIMELINE,ticks).angle());
        }
        void subject(double ticks,Matrix4f view,float centerX,float centerZ,boolean sourceHole) {
            var matrix=new Matrix4f(projection).mul(view); var eye=new Matrix4f(matrix).invert().transform(new Vector4f(0,0,1,0)); eye.div(eye.w);
            double support=sourceHole?32:15.09;
            if(sourceHole && programHasWaves) {var wave=new com.mega.overheaven.foliagecompat.finale.FinaleWaves.State();com.mega.overheaven.foliagecompat.finale.FinaleWaves.evaluate(TIMELINE,ticks,3.2,11,.16,wave);support=Math.max(support,wave.support);}
            if(!bounds.project(matrix,eye,centerX,0,centerZ,support,W,H)) return;
            blit(cosmos,scene,GL_NEAREST);
            solution.bind(); glDisable(GL_SCISSOR_TEST); glClearBufferfv(GL_COLOR,0,new float[]{0,0,0,1}); glClearBufferfv(GL_COLOR,1,new float[]{0,0,0,1}); glClearBufferfv(GL_COLOR,2,new float[4]); glClearBufferfv(GL_COLOR,3,new float[]{1000000,1000000,0,-1});
            glEnable(GL_SCISSOR_TEST); glScissor((int)(bounds.left*solution.width/(float)W),(int)(bounds.bottom*solution.height/(float)H),(int)Math.ceil((bounds.right-bounds.left)*solution.width/(float)W),(int)Math.ceil((bounds.top-bounds.bottom)*solution.height/(float)H));
            integrate.use(); material(integrate,ticks,view,centerX,centerZ,sourceHole,solution.height); draw(); glDisable(GL_SCISSOR_TEST);
            bloom.use(); material(bloom,ticks,view,centerX,centerZ,sourceHole,H); bloom.pair("FullSize",W,H); bloom.texture("FrontTexture",0,solution.textures[0]); bloom.texture("BackTexture",1,solution.textures[1]); bloom.texture("Transmission",2,surface.textures[0]); bloom.texture("LensTexture",3,solution.textures[2]); pyramid.render(bloom,layerMode==1?2:layerMode==2?3:1,vao);
            if(glare!=null) {
                var frame=TIMELINE.frame(ticks);
                double amount=sourceHole?1:0;
                if(frame.phase()==FinaleTimeline.Phase.EXPOSE) amount*=(programHasIntro?com.mega.overheaven.foliagecompat.finale.CausalTransition.reveal(frame.progress()):FinaleTimeline.smooth(0,.01875,frame.progress()));
                if(frame.phase()==FinaleTimeline.Phase.RETURN) amount*=1-FinaleTimeline.smooth(0,1,frame.progress());
                var intro=new com.mega.overheaven.foliagecompat.finale.CausalTransition.State();
                if(sourceHole&&programHasIntro)com.mega.overheaven.foliagecompat.finale.CausalTransition.evaluate(TIMELINE,ticks,-1,intro);
                if(glare.project(matrix,eye,centerX,0,centerZ,3.2,glareEnabled?amount:0,1,3.2,frame.phase().ordinal(),frame.progress(),W,H,intro.revealGlare)) {
                    GlProgram sourceProgram=glare.begin(); material(sourceProgram,ticks,view,centerX,centerZ,sourceHole,H);
                    sourceProgram.texture("Transmission",2,surface.textures[0]); draw();
                }
            }
            cosmos.bind(); composite.use(); material(composite,ticks,view,centerX,centerZ,sourceHole,H); composite.texture("Scene",0,scene.textures[0]); composite.texture("Opaque",1,sceneOpaqueOverride==0?scene.textures[0]:sceneOpaqueOverride); composite.texture("Transmission",2,surface.textures[0]); composite.texture("FrontTexture",3,solution.textures[0]); composite.texture("BackTexture",4,solution.textures[1]); composite.texture("LensTexture",5,solution.textures[2]); composite.texture("MetaTexture",6,solution.textures[3]); composite.texture("Bloom",7,pyramid.result().textures[0]); composite.integer("DisplaySpace",displaySpace); composite.integer("LayerMode",layerMode); if(glare!=null) glare.bind(composite); draw();
        }
        void compose(double ticks,double stop) {
            var exposure=TIMELINE.exposure(ticks,stop); result.bind(); late.use();
            var introImpact=new com.mega.overheaven.foliagecompat.finale.CausalTransition.State();
            if(programHasIntro && impactEnabled)com.mega.overheaven.foliagecompat.finale.CausalTransition.evaluate(TIMELINE,ticks,stop,introImpact);
            CausalUniforms.applyImpact(late,introImpact); late.scalar("CameraExposure",programHasIntro?com.mega.overheaven.foliagecompat.finale.CausalTransition.exposure(TIMELINE,ticks,stop):0); late.texture("Scene",0,world.textures[0]); late.texture("Cosmos",1,cosmos.textures[0]); late.scalar("Coverage",exposure.coverage()); late.scalar("Flash",exposure.white()); late.scalar("Pulse",exposure.pulse()); late.integer("Black",stop<0 && FinaleTimeline.black(TIMELINE.frame(ticks))?1:0); draw();
        }
        @Override public void close() {
            for(var program:new GlProgram[]{stars,integrate,composite,bloom,late,copy}) program.close();
            for(var target:new GlTarget[]{world,background,cosmos,scene,result,starLow,opaque,surface,solution}) target.close();
            pyramid.close(); disk.close(); if(nasa!=null) nasa.close();
            if(glare!=null) glare.close();
        }
    }
}
