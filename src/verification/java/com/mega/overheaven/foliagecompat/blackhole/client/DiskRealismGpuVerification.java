package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.glfw.GLFW.*;
import static com.mega.overheaven.foliagecompat.blackhole.client.RenderStabilityGpuVerification.image;
import static com.mega.overheaven.foliagecompat.blackhole.client.RenderStabilityGpuVerification.paired;
import org.lwjgl.opengl.GL;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Equal production pass orchestration on both sides; no Minecraft/host pipeline launch. */
public final class DiskRealismGpuVerification {
    private static Path source,output;
    private static int checks,vao;
    private static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message);checks++; }
    private static void draw() { glBindVertexArray(vao);glDrawArrays(GL_TRIANGLES,0,3); }
    private static final class Pipeline extends RenderStabilityGpuVerification.Pipeline {
        float rotation=.25f,tilt=30,yaw=-83,inner=3.8f,outer=11,brightness=3.2f;
        boolean darkScene,uniform;int uniformTexture;
        Pipeline(Path dir)throws Exception {
            super(false,dir);
            uniformTexture=glGenTextures();glBindTexture(GL_TEXTURE_2D,uniformTexture);
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA16F,1,1,0,GL_RGBA,GL_FLOAT,new float[]{.7f,.7f,.7f,1});
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            glowStrength=.7f;
        }
        @Override void inputs() {
            super.inputs();
            if(darkScene) for(var t:new GlTarget[]{opaque,scene}) {t.bind();glClearBufferfv(GL_COLOR,0,new float[]{.004f,.005f,.008f,1});}
        }
        @Override void material(GlProgram p,boolean solving) {
            super.material(p,solving);
            double t=Math.toRadians(tilt),y=Math.toRadians(yaw);
            p.vector("DiskNormal",(float)(Math.sin(y)*Math.sin(t)),(float)Math.cos(t),(float)(Math.cos(y)*Math.sin(t)));
            p.vector("DiskRight",(float)Math.cos(y),0,(float)-Math.sin(y));
            p.vector("DiskForward",(float)(-Math.sin(y)*Math.cos(t)),(float)Math.sin(t),(float)(-Math.cos(y)*Math.cos(t)));
            p.scalar("Rotation",rotation);p.scalar("Inner",inner);p.scalar("Outer",outer);p.scalar("Brightness",brightness);
            advection.evaluate(time,rotation,.12,outer-inner,12);advection.apply(p);
            if(uniform)p.texture("DiskTexture",10,uniformTexture);
        }
        @Override public void close() {super.close();glDeleteTextures(uniformTexture);}
    }
    private static void png(GlTarget target,String name)throws Exception {
        float[] pixels=image(target,0);BufferedImage result=new BufferedImage(target.width,target.height,BufferedImage.TYPE_INT_RGB);
        // Diagnostic material panels use one shared lower exposure to show the signed
        // radiance difference without saturating; actual-texture comparisons keep gain 1.
        double gain=name.startsWith("uniform-")?.08:1.;
        for(int y=0;y<target.height;y++) for(int x=0;x<target.width;x++) {
            int packed=0;
            for(int c=0;c<3;c++){double v=Math.max(0,pixels[(y*target.width+x)*4+c])*gain;v=v/(1+v);packed=(packed<<8)|(int)Math.round(Math.pow(v,1/2.2)*255);}
            result.setRGB(x,target.height-1-y,packed);
        }
        ImageIO.write(result,"png",output.resolve(name+".png").toFile());
    }
    private static double energy(float[] pixels,int width,boolean left) {
        double sum=0;for(int i=0;i<pixels.length;i+=4) if(((i/4)%width<width/2)==left)sum+=pixels[i]+pixels[i+1]+pixels[i+2];return sum;
    }
    private static double maxDifference(float[] a,float[] b) {
        double max=0;for(int i=0;i<a.length;i++){check(Float.isFinite(a[i])&&Float.isFinite(b[i]),"finite output");max=Math.max(max,Math.abs(a[i]-b[i]));}return max;
    }
    private static void probes(Pipeline p)throws Exception {
        String fragment="#version 330 core\nin vec2 texCoord;out vec4 Color;\n"+VerificationShaders.read(source,"field.glsl")+"""
            uniform int Probe;uniform float Factor;
            void main(){
                float mu=texCoord.x*2.-1.;
                if(Probe==0) Color=vec4(diskDoppler(vec3(1,0,0),vec3(0,-sqrt(max(0.,1.-mu*mu)),mu),Inner*Factor),1);
                else if(Probe==1) Color=vec4(diskRadialEmission(mix(Inner,Outer,texCoord.x)),0,0,1);
                else Color=vec4(introHorizonRim(coreRadius(),.01),wideBloomGain());
            }
            """;
        try(var program=new GlProgram(VerificationShaders.read(source,"fullscreen.vsh"),fragment);var target=new GlTarget(GL_RGBA32F)) {
            target.resize(256,1);
            for(float rotation:new float[]{0,.25f,-.25f,4,-4})for(float factor:new float[]{1,2,3}) {
                program.use();p.material(program,false);program.vector("DiskRight",1,0,0);program.vector("DiskForward",0,0,-1);program.scalar("Rotation",rotation);program.scalar("Factor",factor);program.integer("Probe",0);target.bind();draw();
                float[] a=image(target,0);
                double beta=Math.min(.45,.30*Math.sqrt(Math.abs(rotation)/.25)/Math.sqrt(factor));
                for(int x=0;x<256;x++) {
                    double mu=((x+.5)/256*2-1)*Math.signum(rotation),d=Math.sqrt(1-beta*beta)/(1-beta*mu),gain=d*d*d;
                    check(Math.abs(a[x*4+1]-gain)<2e-5,"Doppler D^3 and velocity sign");
                    check(Math.abs(a[x*4]/gain-1)<=.08001&&Math.abs(a[x*4+2]/gain-1)<=.08001,"bounded color shift");
                }
            }
            for(float outer:new float[]{3.801f,3.9f,4.1f,5,11,128}) {
                program.use();p.material(program,false);program.scalar("Outer",outer);program.integer("Probe",1);target.bind();draw();float[] a=image(target,0);double maximum=0;
                for(int i=0;i<a.length;i+=4){check(a[i]>=0&&a[i]<=1.0001,"radial emissivity normalized and finite");maximum=Math.max(maximum,a[i]);}
                check(maximum>.985,"narrow/wide disk retains emissive band");
            }
            program.use();p.material(program,false);program.integer("Probe",2);target.bind();draw();float[] a=image(target,0);
            check(a[0]==0 && Math.abs(a[3]-.4)<1e-6,"ordinary horizon emits no rim; widest bloom at 40 percent");
            program.integer("IntroEnabled",1);program.scalar("IntroTighten",1);program.scalar("IntroCapture",1);program.scalar("IntroBodyScale",1);draw();a=image(target,0);
            check(a[0]>0 && a[3]==1,"tightening retains cinematic rim and bloom");
            program.integer("IntroEnabled",0);program.scalar("FinaleAmount",1);draw();a=image(target,0);
            check(a[0]==0 && a[3]==1,"singularity glow unchanged");
        }
        System.out.println("PASS GPU uniform-material Doppler formula, signed velocity, color bounds, narrow disks and intro-only rim");
    }
    private static void directionChecks(Pipeline p)throws Exception {
        p.resize(640,360);p.darkScene=true;p.constantDepth=true;p.split=false;p.uniform=true;p.yaw=0;p.glowStrength=0;p.tilt=30;p.distance=35;p.inputs();
        float[][] samples=new float[3][];int index=0;
        for(float rotation:new float[]{.25f,-.25f,0}) {
            p.rotation=rotation;p.frame(1);samples[index++]=image(p.out,0);
            png(p.out,rotation>0?"uniform-positive":rotation<0?"uniform-negative":"uniform-zero");
        }
        double positive=energy(samples[0],p.width,true)/energy(samples[0],p.width,false);
        double negative=energy(samples[1],p.width,true)/energy(samples[1],p.width,false);
        double zero=energy(samples[2],p.width,true)/energy(samples[2],p.width,false);
        check(positive>1.15 && negative<1/1.15 && Math.abs(zero-1)<.01,"approaching side follows actual rotation; zero rotation symmetric");
        p.rotation=.25f;p.frame(1);float[] paused=image(p.out,0);p.frame(1);check(Arrays.equals(paused,image(p.out,0)),"paused time preserves every pixel and Doppler side");
        p.tilt=90;p.frame(1);float[] face=image(p.out,0);check(Math.abs(energy(face,p.width,true)/energy(face,p.width,false)-1)<.01,"face-on disk symmetric");
        p.tilt=30;p.frame(1);float[] field=image(p.solution,0),meta=image(p.solution,3);int foreground=0,dark=0;
        for(int i=0;i<field.length;i+=4)if(meta[i+2]>.5){if(field[i]+field[i+1]+field[i+2]>.001)foreground++;else dark++;}
        check(foreground>0 && dark>0,"captured core preserves foreground disk emission while inner shadow stays dark");
        System.out.printf(Locale.ROOT,"DIRECTION left/right positive=%.3f negative=%.3f zero=%.3f captured-front=%d dark=%d%n",positive,negative,zero,foreground,dark);
        p.uniform=false;p.rotation=.25f;p.glowStrength=.7f;p.yaw=-83;p.darkScene=false;p.constantDepth=false;
    }
    private static void pictures(Pipeline p,Pipeline old)throws Exception {
        for(var q:new Pipeline[]{p,old}){q.resize(960,540);q.split=false;q.constantDepth=true;q.darkScene=true;q.glowStrength=.7f;q.inputs();}
        String[] names={"default","close","far","face","edge","back","negative","zero","no-glow","narrow","positive"};
        for(int test=0;test<names.length;test++) {
            for(var q:new Pipeline[]{old,p}) {
                q.distance=test==1?22:test==2?90:35;q.yaw=test==0||test==1||test==2||test==8?-83:0;
                q.tilt=test==3?90:test==4?5:test==5?-30:30;q.rotation=test==6?-.25f:test==7?0:.25f;q.glowStrength=test==8?0:.7f;q.outer=test==9?4.3f:11;
                q.frame(1);png(q.out,(q==p?"after-":"before-")+names[test]);
            }
        }
        for(var q:new Pipeline[]{p,old}){q.distance=35;q.tilt=30;q.yaw=-83;q.rotation=.25f;q.glowStrength=.7f;q.outer=11;q.darkScene=false;q.constantDepth=false;q.inputs();}
    }
    private static void invariants(Pipeline p,Pipeline old)throws Exception {
        for(var q:new Pipeline[]{p,old}){q.resize(640,360);q.distance=35;q.split=false;q.glowStrength=.7f;q.inputs();q.bounds();q.solve();}
        for(int attachment:new int[]{0,1}) {
            float[] a=image(p.solution,attachment),b=image(old.solution,attachment);
            for(int i=3;i<a.length;i+=4)check(a[i]==b[i],"default disk absorption unchanged by emissivity and Doppler");
        }
        for(int shape=0;shape<=3;shape++) {
            p.shape=shape;p.split=true;p.inputs();p.frame(1);float[] cached=image(p.front,0);p.compose(p.reference,2);
            check(RenderStabilityGpuVerification.difference(cached,image(p.reference,0),p.glow,p.width)<.003,"front cache matches resolve across walls/leaves/transparent segmentation");
            if(shape==3){float[] out=image(p.out,0),host=image(p.scene,0);check(maxDifference(out,host)<.001,"full wall entirely blocks subject");}
        }
        p.shape=0;p.split=false;
        for(double time:new double[]{4.0,4.35,4.4,4.475,4.55,8,40.4,41,88,108,114}) {
            for(var q:new Pipeline[]{old,p}){q.finaleTicks=time*20;q.constantDepth=time>40.9;q.inputs();q.frame(1);}
            if(time>=4.55)check(maxDifference(image(p.out,0),image(old.out,0))<.002,"singularity and later finale unchanged t="+time);
        }
        for(var q:new Pipeline[]{p,old}){q.finaleTicks=-1;q.constantDepth=false;q.inputs();}
        RenderStabilityGpuVerification.resourceChecks(p);
        System.out.println("PASS absorption, foreground cache, opaque/transparent guards, unchanged singularity/finale, resize/release and GL restoration");
    }
    private static void bloomChecks(Pipeline p,Pipeline old) {
        for(int steps:new int[]{24,48,96}) {
            double[] values=new double[2];int index=0;
            for(var q:new Pipeline[]{old,p}) {
                q.steps=steps;q.constantDepth=true;q.brightness=0;q.resize(640,360);q.rect.set(0,0,q.solution.width,q.solution.height);q.glow.set(0,0,640,360);
                q.solution.bind();glDisable(GL_SCISSOR_TEST);glClearBufferfv(GL_COLOR,0,new float[]{.5f,.5f,.5f,1});glClearBufferfv(GL_COLOR,1,new float[]{0,0,0,1});glClearBufferfv(GL_COLOR,2,new float[]{0,0,10,65504});
                q.bloom(1);float[] pixel=new float[4];q.bloom.result().bind();glReadPixels(q.bloom.result().width/2,q.bloom.result().height/2,1,1,GL_RGBA,GL_FLOAT,pixel);values[index++]=pixel[0];
            }
            int levels=GlBloom.levels(steps);double expected=(levels-2+2*.4)/levels;
            check(Math.abs(values[1]/values[0]-expected)<.002,"largest two bloom scales reduced once; smaller levels and original denominator retained steps="+steps);
        }
        for(var q:new Pipeline[]{old,p}){q.steps=48;q.constantDepth=false;q.brightness=3.2f;q.resize(640,360);q.inputs();}
        System.out.println("PASS bloom scale gains: low .60 / balanced .70 / high .76 total for uniform emission; singularity remains 1");
    }
    private static void performance(Pipeline p,Pipeline old) {
        for(var q:new Pipeline[]{p,old}){q.resize(1920,1080);q.glowStrength=.7f;q.outer=11;q.shape=0;q.split=false;q.inputs();}
        for(int test=0;test<6;test++) {
            int bodies=test==5?3:1;
            for(var q:new Pipeline[]{p,old}){q.distance=test==0?120:22;q.split=test==2||test==3;q.shape=test==3||test==4?1:0;q.inputs();}
            double[] result=paired(()->old.frame(bodies),()->p.frame(bodies));double budget=Math.max(.1,result[0]*.05);
            System.out.printf(Locale.ROOT,"BENCH 1920x1080 balanced scenario=%d subjects=%d before=%.3f after=%.3f delta=%+.3f budget=%.3f ms%n",test,bodies,result[0],result[1],result[1]-result[0],budget);
            check(result[1]-result[0]<=budget,"GPU incremental budget scenario="+test);
        }
    }
    public static void main(String[] args)throws Exception {
        source=Path.of(args[0]);output=Path.of(args[2]);Files.createDirectories(output);
        check(glfwInit(),"GLFW");glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        long window=glfwCreateWindow(64,64,"disk verification",0,0);check(window!=0,"context");glfwMakeContextCurrent(window);GL.createCapabilities();vao=glGenVertexArrays();RenderStabilityGpuVerification.vao=vao;
        System.out.println("GPU: "+glGetString(GL_RENDERER));
        try(var p=new Pipeline(source);var old=new Pipeline(Path.of(args[1]))) {
            probes(p);directionChecks(p);pictures(p,old);invariants(p,old);bloomChecks(p,old);
            if(args.length<4 || !args[3].equals("checks"))performance(p,old);
            check(glGetError()==GL_NO_ERROR,"no GL errors");
        }finally{glUseProgram(0);glDeleteVertexArrays(vao);glfwDestroyWindow(window);glfwTerminate();}
        System.out.println("PASS "+checks+" checks. Synthetic GPU results; host shader-pack integration still requires in-game acceptance.");
    }
}
