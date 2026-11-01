package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static com.mega.overheaven.foliagecompat.blackhole.client.FinaleGpuVerification.*;
import com.mega.overheaven.foliagecompat.finale.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.*;
import java.util.function.Consumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;

/** Production star passes plus geometric/photometric fixtures. No Minecraft is started. */
public final class StarTimelapseGpuVerification {
    private static final FinaleTimeline TIMELINE=FinaleTimeline.defaults();
    private static final FinaleStarExposure EXPOSURE=new FinaleStarExposure();
    private static final StringBuilder REPORT=new StringBuilder();
    private static void expect(boolean ok,String message) { check(ok,message);REPORT.append("PASS ").append(message).append('\n'); }
    private static double energy(float[] a) { double result=0;for(int i=0;i<a.length;i+=4)result+=a[i]+a[i+1]+a[i+2];return result; }
    private static double difference(float[] a,float[] b) { double result=0;for(int i=0;i<a.length;i+=4)for(int c=0;c<3;c++)result+=Math.abs(a[i+c]-b[i+c]);return result; }
    private static void finite(float[] a,String label) { for(float v:a)if(!Float.isFinite(v)||v<0)throw new AssertionError(label);expect(true,label); }
    private static Consumer<GlProgram> setup(double seconds,Matrix4f view,int detail,boolean exposed) {
        double ticks=seconds*20;var frame=TIMELINE.frame(ticks);EXPOSURE.evaluate(TIMELINE,ticks,detail);
        return p->{uniforms(p,frame.phase().ordinal(),(float)frame.progress(),ticks,view);applyExposure(p,ticks);p.integer("Detail",detail);p.integer("ExposureEnabled",exposed?1:0);};
    }
    private static void applyExposure(GlProgram p,double ticks) {
        if(glGetUniformLocation(p.handle(),"ShortExposureAngles[0]")>=0) {
            // Exact previous temporal sampler for the saved baseline, not a new-array upload.
            float[] full=new float[13],shortTrail=new float[13];
            for(int i=0;i<=EXPOSURE.segments && EXPOSURE.segments>0;i++) {
                full[i]=(float)(FinaleChoreography.starMotion(TIMELINE,ticks-EXPOSURE.window*i/EXPOSURE.segments).angle()-EXPOSURE.angle);
                shortTrail[i]=(float)(FinaleChoreography.starMotion(TIMELINE,ticks-EXPOSURE.window*.35*i/EXPOSURE.segments).angle()-EXPOSURE.angle);
            }
            p.integer("ExposureEnabled",EXPOSURE.enabled?1:0);p.scalar("ExposureSeconds",EXPOSURE.seconds);
            p.floats("ExposureAngles[0]",full);p.floats("ShortExposureAngles[0]",shortTrail);
            p.floats("GalaxyAngles[0]",EXPOSURE.galaxy);p.integer("GalaxySamples",EXPOSURE.galaxySamples);
        } else StarExposureUniforms.apply(p,EXPOSURE);
    }
    private static void sky(FinaleStarMap map,GlTarget low,GlTarget high,double seconds,Matrix4f view,int detail,boolean enabled) {
        try(var state=GlState.capture()) {
            GlState.fullscreen();var params=setup(seconds,view,detail,enabled);low.bind();
            if(!map.drawBackground(params,vao))throw new AssertionError("NASA load failed");
            glBindFramebuffer(GL_READ_FRAMEBUFFER,low.framebuffer);glBindFramebuffer(GL_DRAW_FRAMEBUFFER,high.framebuffer);glBlitFramebuffer(0,0,low.width,low.height,0,0,high.width,high.height,GL_COLOR_BUFFER_BIT,GL_LINEAR);
            high.bind();map.drawStars(params,high.width,high.height,detail);
        }
    }
    private static double gpuTime(Runnable work) {
        int q=glGenQueries();glBeginQuery(GL_TIME_ELAPSED,q);
        for(int i=0;i<32;i++)work.run();glEndQuery(GL_TIME_ELAPSED);
        double result=glGetQueryObjectui64(q,GL_QUERY_RESULT)/32e6;glDeleteQueries(q);return result;
    }
    private static double[] measurePair(Runnable old,Runnable now) {
        for(int i=0;i<48;i++){old.run();now.run();}
        double[] before=new double[7],after=new double[7];
        for(int batch=0;batch<7;batch++) {
            if(batch%2==0){before[batch]=gpuTime(old);after[batch]=gpuTime(now);}
            else{after[batch]=gpuTime(now);before[batch]=gpuTime(old);}
        }
        Arrays.sort(before);Arrays.sort(after);return new double[]{before[3],after[3]};
    }
    private static BufferedImage capture(GlTarget target,int width,int height) {
        float[] p=pixels(target);var full=new BufferedImage(target.width,target.height,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<target.height;y++)for(int x=0;x<target.width;x++){int at=(y*target.width+x)*4,rgb=0;for(int c=0;c<3;c++)rgb|=Math.max(0,Math.min(255,Math.round(p[at+c]*255)))<<(16-8*c);full.setRGB(x,target.height-1-y,rgb);}
        var small=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);var g=small.createGraphics();g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(full,0,0,width,height,null);g.dispose();return small;
    }
    private static String oldSource(Path before,String name) {
        try { return VerificationShaders.read(before,name); }
        catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
    }
    private static float[] single(GlProgram p,GlTarget target,int starVao,int buffer,Vector3f star,int segments,boolean enabled,float extinction,float fov,float span,Matrix4f view) {
        return single(p,target,starVao,buffer,star,segments,enabled,extinction,fov,span,view,1,1);
    }
    private static float[] single(GlProgram p,GlTarget target,int starVao,int buffer,Vector3f star,int segments,boolean enabled,float extinction,float fov,float span,Matrix4f view,int instances,int bright) {
        target.bind();glClearBufferfv(GL_COLOR,0,new float[]{0,0,0,1});GlState.fullscreen();p.use();
        float[] records=new float[instances*7];
        for(int i=0;i<instances;i++)System.arraycopy(new float[]{star.x,star.y,star.z,1,.8f,.6f,2},0,records,i*7,7);
        glBindVertexArray(starVao);glBindBuffer(GL_ARRAY_BUFFER,buffer);glBufferData(GL_ARRAY_BUFFER,records,GL_DYNAMIC_DRAW);
        Matrix4f vp=new Matrix4f().perspective((float)Math.toRadians(fov),target.width/(float)target.height,.05f,4096).mul(view);
        p.matrix("VP",vp);p.matrix("InverseVP",new Matrix4f(vp).invert());p.vector("Eye",0,0,0);p.pair("FullSize",target.width,target.height);
        p.scalar("OrbitAngle",0);p.scalar("Seed",7);p.scalar("Extinction",extinction);p.integer("Black",0);p.integer("Detail",1);
        p.integer("ExposureEnabled",enabled?1:0);p.integer("TrailSegments",segments);p.integer("BrightStars",bright);
        float[] angles=new float[65],travel=new float[65];for(int i=0;i<=64;i++){angles[i]=span*i/64;travel[i]=Math.abs(angles[i]);}
        p.floats("ExposureAngles[0]",angles);p.floats("ExposureTravel[0]",travel);
        glEnable(GL_BLEND);glBlendFunc(GL_ONE,GL_ONE);glDrawArraysInstanced(GL_TRIANGLES,0,6*(segments+1),instances);glDisable(GL_BLEND);return pixels(target);
    }
    public static void main(String[] args)throws Exception {
        source=Path.of(args[0]);output=Path.of(args[1]);Path before=Path.of(args[2]);Files.createDirectories(output);Files.createDirectories(output.resolve("frames"));
        expect(glfwInit(),"GLFW initialized");glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
        long window=glfwCreateWindow(64,64,"star exposure verification",0,0);expect(window!=0,"hidden context");glfwMakeContextCurrent(window);GL.createCapabilities();vao=glGenVertexArrays();
        REPORT.append("GPU ").append(glGetString(GL_RENDERER)).append("; independent 1920x1080 production GLSL/NASA stars, no Minecraft/host execution.\n");
        try(var pipeline=new FinaleFlashGpuVerification.Pipeline();var oldSky=new FinaleStarMap(name->Files.newInputStream(source.resolve("../../textures/finale/"+name)),name->oldSource(before,name));var low=new GlTarget(GL_RGBA16F).linear();var high=new GlTarget(GL_RGBA16F);var singleProgram=new GlProgram(read("finale_star_points.vsh"),read("finale_star_points.fsh"))) {
            pipeline.resize(48,.5f);low.resize(W/2,H/2);high.resize(W,H);oldSky.prepare();while(oldSky.loading())Thread.sleep(5);GlState.fullscreen();
            // Warm both paths before comparison so initial shader specialization is excluded.
            for(int warm=0;warm<32;warm++){sky(oldSky,low,high,93,new Matrix4f(),1,true);sky(pipeline.nasa,low,high,93,new Matrix4f(),1,true);}
            expect(glGetError()==GL_NO_ERROR,"production shaders link and baseline/current warmup is GL-error free");
            int starVao=glGenVertexArrays(),buffer=glGenBuffers();glBindVertexArray(starVao);glBindBuffer(GL_ARRAY_BUFFER,buffer);
            for(int i=0;i<3;i++){glEnableVertexAttribArray(i);glVertexAttribPointer(i,i==2?1:3,GL_FLOAT,false,28,i==0?0:i==1?12:24);glVertexAttribDivisor(i,1);}
            try {
                Vector3f star=new Vector3f(.1f,.2f,-1).normalize();var view=new Matrix4f();
                double baseEnergy=energy(single(singleProgram,high,starVao,buffer,star,8,false,0,70,.35f,view));
                double firstEnergy=0;
                for(int n:new int[]{4,8,12}) {
                    float[] rendered=single(singleProgram,high,starVao,buffer,star,n,true,0,70,.35f,view);finite(rendered,"finite curved track segments="+n);
                    double sum=energy(rendered);REPORT.append(String.format(Locale.ROOT,"single star %d segments energy %.6f vs head %.6f ratio %.5f%n",n,sum,baseEnergy,sum/baseEnergy));
                    if(firstEnergy==0)firstEnergy=sum;expect(Math.abs(sum/firstEnergy-1)<.02,"intentional gain independent of segment count="+n);expect(sum/baseEnergy>5 && sum/baseEnergy<25,"bounded intentional long-track gain="+n);image(high,"arc-segments-"+n);
                }
                String neutral=read("finale_star_points.vsh").replace("SoftTint=dreamTint(identity);","SoftTint=vec3(1.);").replace("if(gl_InstanceID<64)","if(false)");
                try(var photometry=new GlProgram(neutral,read("finale_star_points.fsh"))) {
                    double head=energy(single(photometry,high,starVao,buffer,star,8,false,0,70,.35f,view));
                    float latitude=new Vector3f(star).cross(new Vector3f(.24f,.91f,-.34f).normalize()).length();
                    double arc=.35*latitude,x=Math.min(1,arc/Math.toRadians(20));
                    double gain=1+23*x*x*(3-2*x),expected=1+1.08/1.6*gain*1.35;
                    for(int n:new int[]{4,8,12}) {
                        double ratio=energy(single(photometry,high,starVao,buffer,star,n,true,0,70,.35f,view))/head;
                        REPORT.append(String.format(Locale.ROOT,"neutral photometry %d segments ratio %.5f analytic %.5f%n",n,ratio,expected));
                        expect(Math.abs(ratio/expected-1)<.02,"radiance follows capped analytic gain and normalized soft skirt "+n);
                    }
                    double compact=energy(single(photometry,high,starVao,buffer,star,8,true,0,70,0,view));
                    expect(Math.abs(compact/head-1)<.001,"collapsed trajectory contributes only independent sharp head");
                }
                try(var noHalo=new GlProgram(read("finale_star_points.vsh").replace("if(gl_InstanceID<64)","if(false)"),read("finale_star_points.fsh"))) {
                    double[] contribution=new double[2];int index=0;
                    for(int count:new int[]{64,65}) {
                        double glowing=energy(single(singleProgram,high,starVao,buffer,star,8,false,0,70,0,view,count,1));
                        double plain=energy(single(noHalo,high,starVao,buffer,star,8,false,0,70,0,view,count,1));
                        contribution[index++]=glowing-plain;
                    }
                    expect(contribution[0]>0 && Math.abs(contribution[1]/contribution[0]-1)<.01,"only first 64 catalog instances have halos");
                    double haloEnergy=0;
                    for(int n:new int[]{4,8,12}) {
                        double e=energy(single(singleProgram,high,starVao,buffer,star,n,false,0,70,0,view));
                        if(haloEnergy==0)haloEnergy=e;
                        expect(Math.abs(e/haloEnergy-1)<.001,"head and halo independent of trail quality="+n);
                    }
                }
                double dimReference=0;
                for(int n:new int[]{4,8,12}) {
                    double e=energy(single(singleProgram,high,starVao,buffer,star,n,true,0,70,.35f,view,1,0));
                    if(dimReference==0)dimReference=e;
                    expect(Math.abs(e/dimReference-1)<.02,"variable 55-85 percent shutter is segment invariant="+n);
                }
                Vector3f pole=new Vector3f(.24f,.91f,-.34f).normalize();Matrix4f look=new Matrix4f().lookAt(0,0,0,pole.x,pole.y,pole.z,0,0,1);
                double poleBase=energy(single(singleProgram,high,starVao,buffer,pole,8,false,0,70,.419f,look));double poleTrail=energy(single(singleProgram,high,starVao,buffer,pole,8,true,0,70,.419f,look));
                expect(Math.abs(poleTrail/poleBase-1)<.03,"rotation-axis star stays compact without energy multiplication");
                Vector3f edgeStar=new Vector3f((float)Math.sin(.8),0,(float)-Math.cos(.8));
                expect(energy(single(singleProgram,high,starVao,buffer,edgeStar,12,false,0,40,.419f,view))==0,"edge fixture head lies outside screen");
                float[] tail=single(singleProgram,high,starVao,buffer,edgeStar,12,true,0,40,.419f,view);expect(energy(tail)>0,"visible tail survives offscreen head");finite(tail,"offscreen clipping finite");image(high,"offscreen-head-tail");
                Vector3f behind=new Vector3f(1,0,.015f).normalize();
                expect(energy(single(singleProgram,high,starVao,buffer,behind,12,false,0,170,.419f,view))==0,"behind-camera fixture head culled");
                tail=single(singleProgram,high,starVao,buffer,behind,12,true,0,170,.419f,view);expect(energy(tail)>0,"visible tail survives behind-camera head at wide FOV");finite(tail,"near-plane segment clipping finite");
                expect(energy(single(singleProgram,high,starVao,buffer,star,8,true,1,70,.35f,view))==0,"completed dissolve removes head and every tail segment");
                String maskSource="#version 330 core\nin vec2 texCoord;out vec4 Color;uniform mat4 InverseVP;uniform float Seed,Extinction;uniform int Detail;\n"+read("finale_sky_common.glsl")+"\nvoid main(){vec4 p=InverseVP*vec4(texCoord*2.-1.,.98,1);vec3 ray=normalize(p.xyz/p.w);float threshold=skyDissolveThreshold(ray);Color=vec4(skyDissolveCoverage(threshold,fwidth(threshold)),threshold,0,1);}";
                try(var maskProgram=new GlProgram(read("fullscreen.vsh"),maskSource);var mask=new GlTarget(GL_RGBA16F)) {
                    mask.resize(W,H);boolean checkedVisible=false,checkedCut=false;
                    for(float extinction:new float[]{.2f,.4f,.6f}) {
                        float[] trail=single(singleProgram,high,starVao,buffer,star,8,true,extinction,70,.419f,view);
                        checkedVisible|=energy(trail)>0;
                        mask.bind();GlState.fullscreen();maskProgram.use();maskProgram.matrix("InverseVP",new Matrix4f().perspective((float)Math.toRadians(70),W/(float)H,.05f,4096).invert());maskProgram.scalar("Seed",7);maskProgram.scalar("Extinction",extinction);maskProgram.integer("Detail",1);draw();
                        float[] dissolve=pixels(mask);boolean clean=true;
                        for(int i=0;i<trail.length;i+=4)if(dissolve[i]==0 && trail[i]+trail[i+1]+trail[i+2]>.00001)clean=false;
                        expect(clean,"arc fragments never fill eroded pixels extinction="+extinction);
                        checkedCut|=energy(trail)==0;
                    }
                    expect(checkedVisible&&checkedCut,"mask fixtures cover visible and extinguished source stars");
                }
                for(float fov:new float[]{40,70,110})for(float aspect:new float[]{1,1.7777778f,2.3333333f}) {
                    high.resize(Math.round(720*aspect),720);float[] result=single(singleProgram,high,starVao,buffer,star,8,true,0,fov,.419f,new Matrix4f().rotationY(.1f));finite(result,"arc FOV and aspect "+fov+"/"+aspect);
                }
                high.resize(W,H);
            }finally{glDeleteBuffers(buffer);glDeleteVertexArrays(starVao);}
            for(double time:new double[]{42,72,93,100}) {
                sky(oldSky,low,high,time,new Matrix4f(),1,true);float[] previousGalaxy=pixels(low);
                sky(pipeline.nasa,low,high,time,new Matrix4f(),1,true);
                expect(difference(previousGalaxy,pixels(low))==0,"Milky Way exposure unchanged t="+time);
            }
            String styleSource="#version 330 core\nin vec2 texCoord;out vec4 Color;\n"+read("finale_star_style.glsl").replace("Seed*.013","7.*.013")+
                "\nvoid main(){float x=texCoord.x;Color=vec4(dreamArcGain(radians(x*30.)),dreamTailGate(radians(x*.5)),dreamHalo(x*24.,12.),dreamShutterIntegral(x));}";
            try(var style=new GlProgram(read("fullscreen.vsh"),styleSource);var styleTarget=new GlTarget(GL_RGBA32F)) {
                styleTarget.resize(512,1);styleTarget.bind();GlState.fullscreen();style.use();draw();float[] values=pixels(styleTarget);boolean bounded=true,monotone=true;
                for(int i=0;i<512;i++){int at=i*4;bounded&=values[at]>=1&&values[at]<=24&&values[at+1]>=0&&values[at+1]<=1;if(i>0)monotone&=values[at]>=values[at-4];if(i>=256)bounded&=values[at+2]==0;}
                expect(bounded&&monotone,"GPU gain capped at 24 monotone gate and finite 12px halo support");glUseProgram(0);
            }
            expect(glGetError()==GL_NO_ERROR,"photometry and style fixtures leave a valid GL state");
            var view=new Matrix4f().rotationZ(.35f);var sheet=new BufferedImage(1440,7*430,BufferedImage.TYPE_INT_RGB);var g=sheet.createGraphics();int row=0;
            for(double seconds:new double[]{42,52,72,88,93,100,106}) {
                sky(oldSky,low,high,seconds,view,1,true);var a=capture(high,720,405);
                sky(pipeline.nasa,low,high,seconds,view,1,true);var b=capture(high,720,405);image(high,String.format(Locale.ROOT,"time-%03.0fs",seconds));finite(pixels(high),"stage finite t="+seconds);
                g.drawImage(a,0,row*430+25,null);g.drawImage(b,720,row*430+25,null);g.setColor(java.awt.Color.WHITE);g.drawString("t="+seconds+"s previous long exposure",12,row*430+17);g.drawString("dream long exposure",732,row*430+17);row++;
                double[] timings=measurePair(()->sky(oldSky,low,high,seconds,view,1,true),()->sky(pipeline.nasa,low,high,seconds,view,1,true));double old=timings[0],now=timings[1];
                REPORT.append(String.format(Locale.ROOT,"balanced t=%.0fs old/new %.3f / %.3f ms delta %+.3f ms%n",seconds,old,now,now-old));
                expect(now-old<1.,"balanced incremental sky cost below 1ms target t="+seconds);
            }
            g.dispose();ImageIO.write(sheet,"png",output.resolve("timelapse-comparison.png").toFile());
            sky(pipeline.nasa,low,high,93,view,1,true);float[] frozen=pixels(high);sky(pipeline.nasa,low,high,93,view,1,true);expect(difference(frozen,pixels(high))==0,"paused and late-joined analytic exposure has no history dependency");
            for(int detail=0;detail<3;detail++)for(float angle:new float[]{0,1.5707963f,-1.5707963f,3.1415927f}) {
                sky(pipeline.nasa,low,high,93,new Matrix4f().rotationX(angle),detail,true);finite(pixels(high),"turn and quality finite "+angle+"/"+detail);
            }
            var axisView=new Matrix4f().lookAt(0,0,0,.24f,.91f,-.34f,0,0,1);
            sky(pipeline.nasa,low,high,93,axisView,1,true);image(high,"rotation-axis-093s");
            var flowView=new Matrix4f().rotationZ(-.45f).mul(new Matrix4f().lookAt(0,0,0,0,-.34f,-.91f,0,1,0));
            sky(pipeline.nasa,low,high,93,flowView,1,true);image(high,"reference-flow-093s");
            sky(pipeline.nasa,low,high,90,flowView,1,true);image(high,"reference-flow-090s");
            sky(oldSky,low,high,90,flowView,1,true);image(high,"reference-flow-before-090s");
            sky(pipeline.nasa,low,high,108,view,1,true);expect(energy(pixels(high))==0,"silence has no star history residue");
            pipeline.cosmos(93*20,view,0,-24,true);expect(difference(pixels(pipeline.cosmos),pixels(pipeline.background))>10,"long-exposure sky preserves subsequent singularity and black hole layers");image(pipeline.cosmos,"subjects-stay-clear");
            for(float progress:new float[]{0,.5f,1}) {high.bind();pipeline.stars.use();var params=setup(88+20*progress,view,1,true);params.accept(pipeline.stars);draw();expect(progress==1?energy(pixels(high))==0:energy(pixels(high))>0,"procedural short-trail fallback follows extinction "+progress);}
            pipeline.compose(818,-1);float[] white=pixels(pipeline.result);boolean solid=true;for(int i=0;i<white.length;i+=4)solid&=white[i]==1&&white[i+1]==1&&white[i+2]==1;expect(solid,"final white priority preserved");pipeline.compose(2288,-1);expect(energy(pixels(pipeline.result))==0,"final black priority preserved");
            // Four real-time excerpts: entry, reversal, long-arc build-up and dissolution peak.
            int frame=0;for(double[] clip:new double[][]{{41,45},{51,55},{70,74},{88,108}})for(double time=clip[0];time<clip[1]-.00001;time+=1./24) {
                sky(pipeline.nasa,low,high,time,view,1,true);ImageIO.write(capture(high,1280,720),"jpg",output.resolve(String.format(Locale.ROOT,"frames/%04d.jpg",frame++)).toFile());
            }
            REPORT.append("Preview: ").append(frame).append(" frames, 24fps, 32s excerpt montage at original playback speed; 1280x720; no sound.\n");
            int fbo=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);glEnable(GL_BLEND);glViewport(3,5,17,19);sky(pipeline.nasa,low,high,72,view,1,true);int[] viewport=new int[4];glGetIntegerv(GL_VIEWPORT,viewport);expect(glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)==fbo&&glIsEnabled(GL_BLEND)&&Arrays.equals(viewport,new int[]{3,5,17,19}),"arc passes restore framebuffer viewport and blend");
            low.resize(640,360);high.resize(1280,720);sky(pipeline.nasa,low,high,72,view,0,true);finite(pixels(high),"resize and quality switch supported");int oldTexture=high.textures[0];high.close();expect(!glIsTexture(oldTexture),"existing target released");oldSky.close();expect(!oldSky.loaded(),"star resources released");expect(glGetError()==GL_NO_ERROR,"OpenGL error free");
        }finally{glDeleteVertexArrays(vao);glfwDestroyWindow(window);glfwTerminate();Files.writeString(output.resolve("report.txt"),REPORT);}
        System.out.println(REPORT);
    }
}
