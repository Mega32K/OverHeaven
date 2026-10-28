package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static com.mega.overheaven.foliagecompat.blackhole.client.FinaleGpuVerification.*;
import com.mega.overheaven.foliagecompat.finale.*;
import java.nio.file.*;
import java.util.*;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;

/** Real production GPU path, synthetic camera and scene; does not launch Minecraft. */
public final class FinaleFeedbackGpuVerification {
    private static final StringBuilder REPORT=new StringBuilder();
    private static void expect(boolean ok,String text){check(ok,text);REPORT.append("PASS ").append(text).append('\n');}
    private static double energy(float[] data){double sum=0;for(int i=0;i<data.length;i+=4)sum+=data[i]+data[i+1]+data[i+2];return sum;}
    private static double difference(float[] a,float[] b){double sum=0;for(int i=0;i<a.length;i+=4)for(int c=0;c<3;c++)sum+=Math.abs(a[i+c]-b[i+c]);return sum/(a.length*.75);}
    private static void finite(float[] a,String label){for(float v:a)if(!Float.isFinite(v))throw new AssertionError(label);expect(true,label);}
    private static double measure(Runnable action){double[] results=new double[3];for(int pass=0;pass<3;pass++){for(int i=0;i<4;i++)action.run();int query=glGenQueries();glBeginQuery(GL_TIME_ELAPSED,query);for(int i=0;i<24;i++)action.run();glEndQuery(GL_TIME_ELAPSED);results[pass]=glGetQueryObjectui64(query,GL_QUERY_RESULT)/24e6;glDeleteQueries(query);}Arrays.sort(results);return results[1];}
    private static void sky(FinaleFlashGpuVerification.Pipeline p,int phase,float progress,double ticks,Matrix4f view,int detail){
        try(var state=GlState.capture()) {
            GlState.fullscreen();p.starLow.bind();boolean ready=p.nasa.drawBackground(s->uniforms(s,phase,progress,ticks,view),vao);
            expect(ready,"NASA asset loaded");p.blit(p.starLow,p.background,GL_LINEAR);p.background.bind();p.nasa.drawStars(s->uniforms(s,phase,progress,ticks,view),W,H,detail);
        }
    }
    private static void late(FinaleFlashGpuVerification.Pipeline p,double chroma,double exposure,double white,boolean black){
        p.result.bind();p.late.use();p.late.texture("Scene",0,p.world.textures[0]);p.late.texture("Cosmos",1,p.world.textures[0]);p.late.scalar("Coverage",0);p.late.scalar("Flash",white);p.late.scalar("Pulse",0);p.late.integer("Black",black?1:0);p.late.pair("FullSize",W,H);p.late.scalar("CameraChroma",chroma);p.late.scalar("CameraExposure",exposure);draw();
    }
    private static float depth(FinaleFlashGpuVerification.Pipeline p,float distance) {
        var clip=p.projection.transform(new org.joml.Vector4f(0,0,-distance,1));return clip.z/clip.w*.5f+.5f;
    }
    private static float[] spectrum(FinaleFlashGpuVerification.Pipeline p,GlProgram fill,boolean enabled,int wall,boolean glass) {
        GlState.fullscreen();p.cosmos.bind();fill.use();draw();p.depth(wall==1?depth(p,8):1);
        if(wall==2) {p.opaque.bind();glEnable(GL_SCISSOR_TEST);glScissor(0,0,W/2,H);glClearBufferfv(GL_COLOR,1,new float[]{depth(p,8),0,0,0});glDisable(GL_SCISSOR_TEST);}
        if(glass) {p.surface.bind();glClearBufferfv(GL_COLOR,0,new float[]{.6f,.8f,.9f,1});glClearBufferfv(GL_COLOR,1,new float[]{depth(p,8),0,0,0});}
        p.spectrumEnabled=enabled;p.subject(848,new Matrix4f(),0,-24,true);return pixels(p.cosmos);
    }
    public static void main(String[] args)throws Exception {
        source=Path.of(args[0]);output=Path.of(args[1]);Files.createDirectories(output);
        expect(glfwInit(),"GLFW");glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
        long window=glfwCreateWindow(64,64,"wave feedback sky",0,0);expect(window!=0,"hidden context");glfwMakeContextCurrent(window);GL.createCapabilities();vao=glGenVertexArrays();
        REPORT.append("GPU ").append(glGetString(GL_RENDERER)).append("; 1920x1080 balanced synthetic production composition; no Minecraft/host execution.\n");
        Path oldSource=Path.of("audit/wave-feedback-sky-2026-10-28/before/src/main/resources/assets/overheaven_foliage_compat/shaders/blackhole");
        try(var p=new FinaleFlashGpuVerification.Pipeline();var old=new FinaleFlashGpuVerification.Pipeline(oldSource,null);
            var fill=new GlProgram(read("fullscreen.vsh"),"#version 330 core\nin vec2 texCoord;out vec4 Color;void main(){float q=step(.5,fract(texCoord.x*32.))*step(.5,fract(texCoord.y*18.));Color=vec4(mix(vec3(.06,.12,.18),vec3(.7,.4,.2),q),1);}")) {
            p.resize(48,.5f);old.resize(48,.5f);GlState.fullscreen();
            float[] noSpectrum=spectrum(p,fill,false,0,false),withSpectrum=spectrum(p,fill,true,0,false);
            expect(difference(noSpectrum,withSpectrum)>.00001,"final wave spectral fringe changes actual background");image(p.cosmos,"wave-spectral-fringe");
            noSpectrum=spectrum(p,fill,false,1,false);withSpectrum=spectrum(p,fill,true,1,false);
            expect(difference(noSpectrum,withSpectrum)==0,"foreground wall rejects spectral fringe");
            noSpectrum=spectrum(p,fill,false,2,false);withSpectrum=spectrum(p,fill,true,2,false);
            double protectedDifference=0,visibleDifference=0;for(int y=0;y<H;y++)for(int x=0;x<W;x++)for(int c=0;c<3;c++) {double delta=Math.abs(noSpectrum[(y*W+x)*4+c]-withSpectrum[(y*W+x)*4+c]);if(x<W/2)protectedDifference=Math.max(protectedDifference,delta);else visibleDifference+=delta;}
            expect(protectedDifference==0 && visibleDifference>1,"partial wall protects foreground while retaining exposed spectrum");
            noSpectrum=spectrum(p,fill,false,0,true);withSpectrum=spectrum(p,fill,true,0,true);
            expect(difference(noSpectrum,withSpectrum)>.000001,"transparent receiver retains spectral background");
            p.spectrumEnabled=true;
            for(float angle:new float[]{0,1.5707963f,-1.5707963f,(float)Math.PI}) {
                Matrix4f view=angle==(float)Math.PI?new Matrix4f().rotationY(angle):new Matrix4f().rotationX(angle);
                sky(p,3,.4f,1000,view,1);float[] image=pixels(p.background);finite(image,"finite all-sky direction "+angle);expect(energy(image)>10,"both celestial hemispheres populated "+angle);image(p.background,"sky-direction-"+angle);
            }
            sky(p,3,.4f,1000,new Matrix4f().rotationY((float)Math.PI),1);float[] seam=pixels(p.background);
            sky(p,3,.4f,1000,new Matrix4f().rotationY(-(float)Math.PI),1);expect(difference(seam,pixels(p.background))<.0001,"periodic longitude seam continuous");
            for(int detail=0;detail<3;detail++){sky(p,3,.4f,1000,new Matrix4f(),detail);finite(pixels(p.background),"quality star batch "+detail);image(p.background,"sky-quality-"+detail);}
            sky(p,3,.4f,1000,new Matrix4f(),1);double starAndGalaxy=energy(pixels(p.background));
            p.blit(p.starLow,p.result,GL_LINEAR);expect(starAndGalaxy>energy(pixels(p.result))+1,"full-resolution instanced stars add resolved light");
            double previous=Double.POSITIVE_INFINITY;
            for(float progress:new float[]{.76f,.84f,.91f,.98f,1}) {
                sky(p,5,progress,1900,new Matrix4f(),1);double e=energy(pixels(p.background));expect(e<=previous,"late star extinction decreases "+progress);previous=e;
            }
            expect(previous==0,"sky extinction endpoint exact black");
            p.world.bind();fill.use();draw();late(p,0,0,0,false);float[] base=pixels(p.result);image(p.result,"feedback-before");
            late(p,1,.9,0,false);float[] active=pixels(p.result);expect(energy(active)>energy(base),"0.2-second exposure increases scene brightness");expect(difference(active,base)>.01,"peak camera feedback visible");finite(active,"peak feedback finite");image(p.result,"feedback-peak");
            late(p,0,0,0,false);expect(difference(base,pixels(p.result))==0,"zero feedback preserves image exactly");
            late(p,1,.9,1,false);float[] solid=pixels(p.result);for(int i=0;i<solid.length;i+=4)if(solid[i]!=1 || solid[i+1]!=1 || solid[i+2]!=1)throw new AssertionError("white");expect(true,"full white overrides chroma/exposure");
            late(p,1,.9,1,true);expect(energy(pixels(p.result))==0,"pure black has highest priority");
            late(p,0,0,0,false);
            for(double ticks:new double[]{808,818,824,848,880,890,905}) {
                p.cosmos(ticks,new Matrix4f(),0,-24,false);p.compose(ticks,-1);finite(pixels(p.result),"final wave remains finite across phases "+ticks);image(p.result,"final-wave-"+(int)ticks);
            }
            old.cosmos(824,new Matrix4f(),0,-64,false);image(old.cosmos,"singularity-sky-before");
            p.cosmos(824,new Matrix4f(),0,-64,false);image(p.cosmos,"singularity-sky-after");
            expect(difference(pixels(p.cosmos),pixels(p.background))>.00001,"stars remain behind singularity and glare");
            p.cosmos(824,new Matrix4f().rotationY((float)Math.PI),0,-24,false);expect(difference(pixels(p.cosmos),pixels(p.background))<.00001,"backward view contains no clamped singularity");
            p.cosmos(824,new Matrix4f().rotationX(-1.55f),0,-1,false);finite(pixels(p.cosmos),"inside upward view");
            for(double ticks:new double[]{808,824,880}) {
                double before=measure(()->old.cosmos(ticks,new Matrix4f(),0,-24,false));
                double after=measure(()->p.cosmos(ticks,new Matrix4f(),0,-24,false));
                REPORT.append(String.format(Locale.ROOT,"GPU full composition ticks=%.0f before=%.3f after=%.3f delta=%+.3f ms (median 3x24)\n",ticks,before,after,after-before));
            }
            var fixedView=new Matrix4f();
            p.starLow.bind();p.stars.use();uniforms(p.stars,3,.4f,1000,fixedView);
            double oldSky=measure(()->{p.starLow.bind();p.stars.use();draw();p.blit(p.starLow,p.background,GL_LINEAR);});
            double newSky=measure(()->{p.starLow.bind();p.nasa.drawBackground(s->uniforms(s,3,.4f,1000,fixedView),vao);p.blit(p.starLow,p.background,GL_LINEAR);p.background.bind();p.nasa.drawStars(s->uniforms(s,3,.4f,1000,fixedView),W,H,1);});
            REPORT.append(String.format(Locale.ROOT,"GPU sky only procedural=%.3f NASA=%.3f delta=%+.3f ms\n",oldSky,newSky,newSky-oldSky));
            double neutral=measure(()->late(p,0,0,0,false)),feedback=measure(()->late(p,1,.9,0,false));
            REPORT.append(String.format(Locale.ROOT,"GPU late pass neutral=%.3f feedback=%.3f delta=%+.3f ms\n",neutral,feedback,feedback-neutral));
            int sentinel=glGenBuffers();glBindBuffer(GL_ARRAY_BUFFER,sentinel);glViewport(3,5,811,607);glActiveTexture(GL_TEXTURE5);glEnable(GL_BLEND);glEnable(GL_DEPTH_TEST);
            p.cosmos(824,new Matrix4f(),0,-24,false);int[] viewport=new int[4];glGetIntegerv(GL_VIEWPORT,viewport);
            expect(glGetInteger(GL_ARRAY_BUFFER_BINDING)==sentinel && Arrays.equals(viewport,new int[]{3,5,811,607}) && glGetInteger(GL_ACTIVE_TEXTURE)==GL_TEXTURE5 && glIsEnabled(GL_BLEND) && glIsEnabled(GL_DEPTH_TEST),"star VBO viewport texture blend depth restored");
            glBindBuffer(GL_ARRAY_BUFFER,0);glDeleteBuffers(sentinel);GlState.fullscreen();
            p.nasa.close();expect(!p.nasa.loaded(),"sky GPU resources released");p.nasa.prepare();while(p.nasa.loading())Thread.sleep(5);
            int unpackBuffer=glGenBuffers();glBindBuffer(GL_PIXEL_UNPACK_BUFFER,unpackBuffer);glBufferData(GL_PIXEL_UNPACK_BUFFER,128,GL_STATIC_DRAW);
            glPixelStorei(GL_UNPACK_ALIGNMENT,8);glPixelStorei(GL_UNPACK_ROW_LENGTH,17);glPixelStorei(GL_UNPACK_SKIP_ROWS,1);glPixelStorei(GL_UNPACK_SKIP_PIXELS,2);glPixelStorei(GL_UNPACK_SWAP_BYTES,GL_TRUE);
            p.nasa.warm();expect(p.nasa.loaded(),"sky resources reconstruct during prewarm");
            expect(glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING)==unpackBuffer && glGetInteger(GL_UNPACK_ALIGNMENT)==8 && glGetInteger(GL_UNPACK_ROW_LENGTH)==17 && glGetInteger(GL_UNPACK_SKIP_ROWS)==1 && glGetInteger(GL_UNPACK_SKIP_PIXELS)==2 && glGetBoolean(GL_UNPACK_SWAP_BYTES),"texture upload preserves host PBO and pixel-store state");
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);glDeleteBuffers(unpackBuffer);glPixelStorei(GL_UNPACK_ALIGNMENT,4);glPixelStorei(GL_UNPACK_ROW_LENGTH,0);glPixelStorei(GL_UNPACK_SKIP_ROWS,0);glPixelStorei(GL_UNPACK_SKIP_PIXELS,0);glPixelStorei(GL_UNPACK_SWAP_BYTES,GL_FALSE);
            sky(p,3,.4f,1000,new Matrix4f(),1);
            try(var unavailable=new FinaleStarMap(name->{throw new java.io.IOException("intentional missing-resource fixture");},name->{try{return read(name);}catch(Exception error){throw new RuntimeException(error);}})) {
                unavailable.prepare();while(unavailable.loading())Thread.sleep(5);p.background.bind();
                boolean loaded=unavailable.drawBackground(s->uniforms(s,3,.4f,1000,new Matrix4f()),vao);
                expect(!loaded && unavailable.path().equals("procedural-fallback") && energy(pixels(p.background))>0,"missing asset falls back without losing the finale");glUseProgram(0);
            }
            var feedbackState=new FinaleWaveFeedback();var timeline=FinaleTimeline.defaults();
            for(int frame=0;frame<150;frame++) {
                double ticks=808+frame*(20.0/30);feedbackState.update(timeline,ticks,3.2,11,.16,0,0,18,18,false,false);
                Matrix4f view=new Matrix4f().rotationXYZ((float)Math.toRadians(feedbackState.pitch),(float)Math.toRadians(feedbackState.yaw),(float)Math.toRadians(feedbackState.roll));
                p.cosmos(ticks,view,0,-18,false);p.world.bind();p.blit(p.cosmos,p.world,GL_NEAREST);late(p,feedbackState.chroma,feedbackState.exposure,timeline.flash(ticks),false);image(p.result,String.format(Locale.ROOT,"preview-%03d",frame));
            }
            expect(glGetError()==GL_NO_ERROR,"production fixtures GL error zero");glUseProgram(0);
        }
        expect(glGetError()==GL_NO_ERROR,"resource cleanup GL error zero");glDeleteVertexArrays(vao);glfwDestroyWindow(window);glfwTerminate();Files.writeString(output.resolve("report.txt"),REPORT);System.out.println(REPORT);
    }
}
