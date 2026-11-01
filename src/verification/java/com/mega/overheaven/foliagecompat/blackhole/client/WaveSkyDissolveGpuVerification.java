package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static com.mega.overheaven.foliagecompat.blackhole.client.FinaleGpuVerification.*;
import com.mega.overheaven.foliagecompat.finale.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import javax.imageio.ImageIO;
import java.util.*;
import java.util.function.Consumer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;

/** Independent production GLSL/asset checks, not a Minecraft or shader-pack certification. */
public final class WaveSkyDissolveGpuVerification {
    private static final StringBuilder REPORT=new StringBuilder();
    private static final FinaleTimeline TIMELINE=FinaleTimeline.defaults();
    private static void expect(boolean ok,String text) { check(ok,text);REPORT.append("PASS ").append(text).append('\n'); }
    private static double energy(float[] a) { double sum=0;for(int i=0;i<a.length;i+=4)sum+=a[i]+a[i+1]+a[i+2];return sum; }
    private static double difference(float[] a,float[] b) { double sum=0;for(int i=0;i<a.length;i+=4)for(int c=0;c<3;c++)sum+=Math.abs(a[i+c]-b[i+c]);return sum; }
    private static void blit(GlTarget from,GlTarget to) { glBindFramebuffer(GL_READ_FRAMEBUFFER,from.framebuffer);glBindFramebuffer(GL_DRAW_FRAMEBUFFER,to.framebuffer);glBlitFramebuffer(0,0,from.width,from.height,0,0,to.width,to.height,GL_COLOR_BUFFER_BIT,GL_LINEAR); }
    private static Consumer<GlProgram> setup(double progress,Matrix4f view,boolean fixed,boolean old,int detail) {
        double start=TIMELINE.start(FinaleTimeline.Phase.EXTINGUISH),duration=TIMELINE.duration(FinaleTimeline.Phase.EXTINGUISH);
        double ticks=start+duration*progress;
        return p->{
            uniforms(p,5,(float)progress,ticks,view);p.integer("Detail",detail);
            if(fixed) { p.scalar("OrbitAngle",0);p.scalar("OrbitSpeed",0); }
            else if(old) {
                double integral=progress*progress*progress*(1-progress*.5);
                p.scalar("OrbitAngle",FinaleChoreography.motion(TIMELINE,start).angle()+duration/20*(-.82*progress+(-2.1+.82)*integral));
                p.scalar("OrbitSpeed",-.82+(-2.1+.82)*FinaleTimeline.smooth(0,1,progress));
            }
        };
    }
    private static void sky(FinaleStarMap map,GlTarget low,GlTarget high,double progress,Matrix4f view,boolean fixed,boolean old,int detail) {
        try(var state=GlState.capture()) {
            GlState.fullscreen();low.bind();var params=setup(progress,view,fixed,old,detail);
            if(!map.drawBackground(params,vao))throw new AssertionError("NASA resources unavailable");
            blit(low,high);high.bind();map.drawStars(params,W,H,detail);
        }
    }
    private static double measure(Runnable work) {
        double[] results=new double[3];
        for(int pass=0;pass<3;pass++) { for(int i=0;i<8;i++)work.run();int query=glGenQueries();glBeginQuery(GL_TIME_ELAPSED,query);for(int i=0;i<32;i++)work.run();glEndQuery(GL_TIME_ELAPSED);results[pass]=glGetQueryObjectui64(query,GL_QUERY_RESULT)/32e6;glDeleteQueries(query); }
        Arrays.sort(results);return results[1];
    }
    private static BufferedImage capture(GlTarget target,int width,int height) {
        float[] data=pixels(target);var full=new BufferedImage(target.width,target.height,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<target.height;y++)for(int x=0;x<target.width;x++) {
            int at=(y*target.width+x)*4,rgb=0;
            for(int c=0;c<3;c++)rgb|=Math.max(0,Math.min(255,Math.round(data[at+c]*255)))<<(16-8*c);
            full.setRGB(x,target.height-1-y,rgb);
        }
        var result=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);Graphics2D g=result.createGraphics();g.drawImage(full,0,0,width,height,null);g.dispose();return result;
    }
    public static void main(String[] args)throws Exception {
        source=Path.of(args[0]);output=Path.of(args[1]);Path before=Path.of(args[2]);Files.createDirectories(output);Files.createDirectories(output.resolve("frames"));
        expect(glfwInit(),"GLFW initialized");glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
        long window=glfwCreateWindow(64,64,"wave sky dissolve verification",0,0);expect(window!=0,"hidden GL context");glfwMakeContextCurrent(window);GL.createCapabilities();vao=glGenVertexArrays();
        REPORT.append("GPU: ").append(glGetString(GL_RENDERER)).append("; 1920x1080 / half-resolution background, full-resolution stars. Synthetic production GLSL and NASA assets; no Minecraft execution.\n");
        String probeSource="#version 330 core\nin vec2 texCoord;out vec4 Color;uniform float Seed,Extinction;uniform int Detail;uniform vec2 ProbeSize;\n"+read("finale_sky_common.glsl")+
            "\nvoid main(){vec2 uv=(gl_FragCoord.xy-.5)/(ProbeSize-1.);float longitude=(uv.x-.5)*6.28318530718,latitude=uv.y*3.14159265359;vec3 direction=vec3(cos(longitude)*sin(latitude),cos(latitude),sin(longitude)*sin(latitude));float threshold=skyDissolveThreshold(direction);Color=vec4(skyDissolveCoverage(threshold,fwidth(threshold)),threshold,starSurvival(direction),1);}";
        try(var pipeline=new FinaleFlashGpuVerification.Pipeline();var oldSky=new FinaleStarMap(name->Files.newInputStream(source.resolve("../../textures/finale/"+name)),name->{try{return VerificationShaders.read(before,name);}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}});
            var low=new GlTarget(GL_RGBA16F).linear();var high=new GlTarget(GL_RGBA16F);var mask=new GlTarget(GL_RGBA16F);var probe=new GlProgram(read("fullscreen.vsh"),probeSource)) {
            pipeline.resize(48,.5f);low.resize(W/2,H/2);high.resize(W,H);mask.resize(512,256);oldSky.prepare();while(oldSky.loading())Thread.sleep(5);
            GlState.fullscreen();probe.use();probe.scalar("Seed",7);probe.integer("Detail",1);probe.pair("ProbeSize",mask.width,mask.height);
            float[] previous=null;double previousAlive=Double.POSITIVE_INFINITY;
            for(float progress:new float[]{0,.15f,.3f,.45f,.6f,.75f,.9f,1}) {
                mask.bind();probe.use();probe.scalar("Extinction",progress);draw();float[] data=pixels(mask);int on=0,off=0,edge=0;boolean monotone=true,finite=true,seam=true;
                for(int i=0;i<data.length;i+=4) { float v=data[i];finite&=Float.isFinite(v)&&v>=0&&v<=1;if(v==1)on++;else if(v==0)off++;else edge++;if(previous!=null&&v>previous[i]+.0001)monotone=false; }
                for(int y=0;y<mask.height;y++)seam&=Math.abs(data[(y*mask.width)*4+1]-data[(y*mask.width+mask.width-1)*4+1])<.003;
                expect(finite&&monotone,"dissolve is finite and cannot restore eroded areas p="+progress);expect(seam,"directional noise is continuous at longitude seam p="+progress);
                if(progress==0)expect(on==mask.width*mask.height,"zero dissolve leaves every direction intact");
                if(progress==1)expect(off==mask.width*mask.height,"completed dissolve is exact black");
                if(progress==.45f)expect(on>1000&&off>1000&&edge<mask.width*mask.height*.12,"partial dissolve retains filled live regions and black holes, only edges feather");
                expect(on<=previousAlive,"sky coverage decreases monotonically p="+progress);previousAlive=on;previous=data;
                REPORT.append(String.format(Locale.ROOT,"mask p=%.2f alive=%.4f edge=%.4f%n",progress,on/(double)(mask.width*mask.height),edge/(double)(mask.width*mask.height)));
            }
            var view=new Matrix4f();
            var sheet=new BufferedImage(1440,4*430,BufferedImage.TYPE_INT_RGB);Graphics2D grid=sheet.createGraphics();int row=0;
            for(double progress:new double[]{0,.35,.55,.75}) {
                sky(oldSky,low,high,progress,view,true,true,1);float[] old=pixels(high);var a=capture(high,720,405);
                sky(pipeline.nasa,low,high,progress,view,true,false,1);float[] current=pixels(high);var b=capture(high,720,405);
                if(progress==0)expect(difference(old,current)<.01,"all-sky source unchanged before extinction");
                else expect(difference(old,current)>1,"threshold dissolution replaces uniform dimming p="+progress);
                grid.drawImage(a,0,row*430+25,null);grid.drawImage(b,720,row*430+25,null);grid.setColor(java.awt.Color.WHITE);grid.drawString(String.format(Locale.ROOT,"p=%.2f  previous fade",progress),12,row*430+17);grid.drawString("new threshold dissolve - fixed direction, exposure retained",732,row*430+17);row++;
                image(high,String.format(Locale.ROOT,"dissolve-%.2f",progress));
            }
            grid.dispose();ImageIO.write(sheet,"png",output.resolve("dissolve-comparison.png").toFile());
            sky(pipeline.nasa,low,high,.5,view,false,false,1);float[] paused=pixels(high);sky(pipeline.nasa,low,high,.5,view,false,false,1);expect(difference(paused,pixels(high))==0,"same synchronized time renders identical sky");
            for(int detail=0;detail<3;detail++)for(float turn:new float[]{0,1.5707963f,-1.5707963f,3.1415927f}) {
                sky(pipeline.nasa,low,high,.25,new Matrix4f().rotationX(turn),false,false,detail);float[] data=pixels(high);boolean finite=true;for(float v:data)finite&=Float.isFinite(v);expect(finite&&energy(data)>0,"both hemispheres and quality levels render detail="+detail+" turn="+turn);
            }
            sky(pipeline.nasa,low,high,1,view,false,false,1);expect(energy(pixels(high))==0,"both NASA background and catalog stars disappear completely");
            for(float progress:new float[]{0,.5f,1}) {high.bind();pipeline.stars.use();uniforms(pipeline.stars,5,progress,1760+400*progress,view);draw();float[] fallback=pixels(high);expect(progress==1?energy(fallback)==0:energy(fallback)>0,"procedural fallback compiles and follows dissolve p="+progress);}
            pipeline.cosmos(1960,view,0,-24,true);expect(difference(pixels(pipeline.cosmos),pixels(pipeline.background))>10,"dissolving sky still preserves singularity and companion black hole");image(pipeline.cosmos,"dissolve-with-subjects");
            for(int id=0;id<7;id++) {
                double start=FinaleWaves.start(TIMELINE,id),charge=FinaleWaves.chargeDuration(TIMELINE,id),flight=FinaleWaves.flightDuration(TIMELINE,id,3.2,11,.16);
                pipeline.cosmos(start+charge+flight*.30,view,0,-24,false);float[] data=pixels(pipeline.cosmos);boolean finite=true;for(float v:data)finite&=Float.isFinite(v);expect(finite&&energy(data)>0,"accelerated wave renders id="+id);
                REPORT.append(String.format(Locale.ROOT,"wave %d start=%.3fs charge=%.4fs flight=%.4fs end=%.3fs%n",id,start/20,charge/20,flight/20,(start+charge+flight)/20));
            }
            pipeline.compose(818,-1);float[] white=pixels(pipeline.result);boolean solid=true;for(int i=0;i<white.length;i+=4)solid&=white[i]==1&&white[i+1]==1&&white[i+2]==1;expect(solid,"40.4s impact white plateau unchanged");
            pipeline.compose(2288,-1);expect(energy(pixels(pipeline.result))==0,"last pure-black phase has final priority");
            for(double progress:new double[]{0,.3,.6,.9}) {
                double old=measure(()->sky(oldSky,low,high,progress,view,false,true,1)),current=measure(()->sky(pipeline.nasa,low,high,progress,view,false,false,1));
                REPORT.append(String.format(Locale.ROOT,"1080p balanced sky p=%.2f old/new %.3f / %.3f ms, delta %+.3f ms%n",progress,old,current,current-old));
            }
            for(int frame=0;frame<480;frame++) {
                double progress=frame/480.;sky(pipeline.nasa,low,high,progress,view,false,false,1);
                ImageIO.write(capture(high,960,540),"jpg",output.resolve(String.format(Locale.ROOT,"frames/%04d.jpg",frame)).toFile());
            }
            int fbo=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);glEnable(GL_BLEND);glViewport(3,5,17,19);sky(pipeline.nasa,low,high,.4,view,false,false,1);
            int[] viewport=new int[4];glGetIntegerv(GL_VIEWPORT,viewport);expect(glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)==fbo&&glIsEnabled(GL_BLEND)&&Arrays.equals(viewport,new int[]{3,5,17,19}),"sky passes restore framebuffer, viewport and blend");
            low.resize(640,360);high.resize(1280,720);expect(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE,"existing targets resize without new ownership");
            int texture=high.textures[0];high.close();expect(!glIsTexture(texture),"sky target released");oldSky.close();expect(!oldSky.loaded(),"star resources released");expect(glGetError()==GL_NO_ERROR,"OpenGL error free");
        } finally { glDeleteVertexArrays(vao);glfwDestroyWindow(window);glfwTerminate();Files.writeString(output.resolve("report.txt"),REPORT); }
        System.out.println(REPORT);
    }
}
