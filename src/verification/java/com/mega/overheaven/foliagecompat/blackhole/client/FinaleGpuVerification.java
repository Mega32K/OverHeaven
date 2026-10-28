package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;

/** Real GLSL in an invisible context. Synthetic scenes, never a Minecraft performance claim. */
public final class FinaleGpuVerification {
    static Path source,output; static int vao,checks; static final int W=1920,H=1080;
    static String read(String file)throws Exception { return VerificationShaders.read(source,file); }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    static void draw(){glBindVertexArray(vao);glDrawArrays(GL_TRIANGLES,0,3);}
    static float[] pixels(GlTarget target) {target.bind();glReadBuffer(GL_COLOR_ATTACHMENT0);var b=BufferUtils.createFloatBuffer(target.width*target.height*4);glReadPixels(0,0,target.width,target.height,GL_RGBA,GL_FLOAT,b);float[] a=new float[b.remaining()];b.get(a);return a;}
    static void image(GlTarget t,String name)throws Exception {float[] p=pixels(t);var img=new BufferedImage(t.width,t.height,BufferedImage.TYPE_INT_RGB);for(int y=0;y<t.height;y++)for(int x=0;x<t.width;x++){int at=(y*t.width+x)*4,rgb=0;for(int c=0;c<3;c++)rgb|=Math.max(0,Math.min(255,Math.round(p[at+c]*255)))<<(16-8*c);img.setRGB(x,t.height-1-y,rgb);}ImageIO.write(img,"png",output.resolve(name+".png").toFile());}
    static void uniforms(GlProgram p,int phase,float progress,double ticks,Matrix4f view){var vp=new Matrix4f().perspective((float)Math.toRadians(70),W/(float)H,.05f,4096).mul(view);var inv=new Matrix4f(vp).invert();var eye=inv.transform(new Vector4f(0,0,1,0));eye.div(eye.w);p.matrix("VP",vp);p.matrix("InverseVP",inv);p.vector("Eye",eye.x,eye.y,eye.z);p.vector("Center",0,0,-24);p.scalar("Time",ticks/20);p.scalar("Seed",7);p.integer("Phase",phase);p.scalar("Progress",progress);p.scalar("Extinction",phase>5?1:phase==5?progress:0);p.integer("Detail",1);var motion=com.mega.overheaven.foliagecompat.finale.FinaleChoreography.motion(com.mega.overheaven.foliagecompat.finale.FinaleTimeline.defaults(),ticks);p.scalar("OrbitAngle",motion.angle());p.scalar("OrbitSpeed",motion.speed());p.scalar("FinaleOrbit",motion.angle());p.integer("StarsOnly",0);p.integer("Black",phase==6 && progress>=.625?1:0);}
    public static void main(String[] args)throws Exception {
        source=Path.of(args[0]);output=Path.of(args[1]);Files.createDirectories(output);
        check(glfwInit(),"GLFW");glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);long window=glfwCreateWindow(64,64,"finale shader verification",0,0);check(window!=0,"context");glfwMakeContextCurrent(window);GL.createCapabilities();vao=glGenVertexArrays();
        String fill="#version 330 core\nin vec2 texCoord;out vec4 Color;void main(){float grid=step(.5,fract(texCoord.x*16.))*step(.5,fract(texCoord.y*9.));Color=vec4(mix(vec3(.12,.16,.2),vec3(.45,.35,.22),grid),1);}";
        try(var stars=new GlProgram(read("fullscreen.vsh"),read("finale_stars.fsh"));var composite=new GlProgram(read("fullscreen.vsh"),read("finale_composite.fsh"));var geometry=new GlProgram(read("finale_geometry.vsh"),read("finale_geometry.fsh"));var near=new GlProgram(read("fullscreen.vsh"),read("integrate.fsh"));var normal=new GlProgram(read("fullscreen.vsh"),read("composite.fsh"));var bloom=new GlProgram(read("fullscreen.vsh"),read("bloom.fsh"));var fillProgram=new GlProgram(read("fullscreen.vsh"),fill);
            var scene=new GlTarget(GL_RGBA16F);var sky=new GlTarget(GL_RGBA16F).linear();var result=new GlTarget(GL_RGBA16F)) {
            check(true,"all production shaders linked");GlState.fullscreen();stars.use();stars.scalar("Radius",3.2);stars.scalar("Glow",.7);stars.vector("DiskRight",1,0,0);stars.vector("DiskNormal",0,.92f,.392f);stars.vector("DiskForward",0,.392f,-.92f);scene.resize(W,H);sky.resize(W/2,H/2);result.resize(W,H);scene.bind();fillProgram.use();draw();
            StringBuilder report=new StringBuilder("GPU: "+glGetString(GL_RENDERER)+"\nSynthetic 1920x1080, 960x540 star solve, seed 7; measured star solve+composite or near solve+bloom+composite; excludes scene capture and Minecraft host\n");
            int[] phases={4,4,4,5,5,5,6,6,7};float[] ps={.1f,.45f,.9f,.1f,.8f,.95f,.3f,.8f,.5f};double[] times={1256,1452,1704,1800,2080,2140,2208,2288,2360};
            var timeline=com.mega.overheaven.foliagecompat.finale.FinaleTimeline.defaults();
            for(int i=0;i<phases.length;i++) {
                int q=glGenQueries();
                for(int n=0;n<28;n++) {
                    if(n==4) glBeginQuery(GL_TIME_ELAPSED,q);
                    sky.bind();stars.use();uniforms(stars,phases[i],ps[i],times[i],new Matrix4f());draw();
                    result.bind();composite.use();uniforms(composite,phases[i],ps[i],times[i],new Matrix4f());composite.scalar("Coverage",timeline.takeover(times[i]));composite.scalar("Flash",timeline.flash(times[i]));composite.scalar("Pulse",Math.sqrt(timeline.flash(times[i])));composite.texture("Scene",0,scene.textures[0]);composite.texture("Cosmos",1,sky.textures[0]);draw();
                }
                glEndQuery(GL_TIME_ELAPSED);double ms=glGetQueryObjectui64(q,GL_QUERY_RESULT)/24e6;glDeleteQueries(q);report.append(String.format(Locale.ROOT,"phase=%d p=%.2f %.3f ms/frame%n",phases[i],ps[i],ms));
                float[] pixels=pixels(result);for(float value:pixels)if(!Float.isFinite(value))throw new AssertionError("nonfinite pixel");check(true,"finite stage "+i);
                if(phases[i]==6 && ps[i]>=.625) { double peak=0;for(int k=0;k<pixels.length;k+=4)peak=Math.max(peak,Math.max(pixels[k],Math.max(pixels[k+1],pixels[k+2])));check(peak==0,"exact black"); }
                image(result,String.format(Locale.ROOT,"stage-%02d-%d-%.2f",i,phases[i],ps[i]));
            }
            result.bind();composite.use();composite.scalar("Coverage",1);composite.scalar("Flash",1);composite.scalar("Pulse",1);composite.integer("Black",0);composite.texture("Scene",0,scene.textures[0]);composite.texture("Cosmos",1,sky.textures[0]);draw();double white=0;for(float value:pixels(result))white=Math.max(white,value);check(white>=.999,"final impact reaches full white");
            nearCases(near,normal,bloom,scene,result,report);
            geometryCases(geometry,result);
            sky.bind();stars.use();uniforms(stars,5,.2f,1840,new Matrix4f().rotateY((float)Math.PI).rotateX(1.2f));draw();image(sky,"backward-upward");
            int original=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);glEnable(GL_BLEND);glViewport(3,5,13,17);try(var saved=GlState.capture()){GlState.fullscreen();scene.bind();}check(glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)==original && glIsEnabled(GL_BLEND),"state restored");
            sky.resize(640,360);sky.bind();check(sky.width==640 && sky.height==360 && glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE,"resize valid");int old=sky.textures[0];sky.close();check(sky.framebuffer==0 && !glIsTexture(old),"release");check(glGetError()==GL_NO_ERROR,"GL clean");
            report.append("Checks passed: "+checks+"\nNo game launched; no host compatibility or server timings inferred.\n");Files.writeString(output.resolve("report.txt"),report);System.out.println(report);
        } finally {glDeleteVertexArrays(vao);glfwDestroyWindow(window);glfwTerminate();}
    }

    static void nearMaterial(GlProgram p,GlTarget opaque,GlTarget surface,GlDiskTexture disk,int phase,float progress,int height) {
        uniforms(p,phase,progress,phase==0?3:phase==1?340:phase==2?800:1100,new Matrix4f());
        p.integer("FinalePhase",phase);p.scalar("FinaleProgress",progress);p.scalar("FinaleTime",phase*18+progress*8);p.scalar("FinaleAmount",1);
        p.vector("DiskNormal",0,.92f,.392f);p.vector("DiskRight",1,0,0);p.vector("DiskForward",0,.392f,-.92f);
        p.vector("HotColor",1,.98f,.94f);p.vector("ColdColor",.6f,.46f,.36f);p.scalar("Radius",3.2);p.scalar("Inner",3.8);p.scalar("Outer",11);p.scalar("Thickness",.12);p.scalar("Brightness",3.2);p.scalar("Glow",1);p.scalar("Lensing",1);p.scalar("Rotation",.25);p.scalar("Infall",.12);p.integer("Steps",48);
        p.scalar("PixelAngle",2.0/(height*1.428));p.texture("OpaqueDepth",8,opaque.textures[1]);p.texture("SurfaceDepth",9,surface.textures[1]);p.texture("DiskTexture",10,disk.handle());
    }
    static void nearCases(GlProgram near,GlProgram composite,GlProgram bloom,GlTarget scene,GlTarget result,StringBuilder report)throws Exception {
        try(var opaque=new GlTarget(GL_RGBA16F,GL_R32F);var surface=new GlTarget(GL_RGBA16F,GL_R32F);var solution=new GlTarget(GL_RGBA16F,GL_RGBA16F,GL_RGBA32F,GL_RGBA32F);var pyramid=new GlBloom();var stream=Files.newInputStream(source.resolve("../../textures/blackhole/disk_detail.png"));var disk=new GlDiskTexture(stream)) {
            opaque.resize(W,H);surface.resize(W,H);solution.resize(W/2,H/2);pyramid.resize(W,H,48);
            opaque.bind();glClearBufferfv(GL_COLOR,0,new float[]{.12f,.16f,.2f,1});glClearBufferfv(GL_COLOR,1,new float[]{1,0,0,0});surface.bind();glClearBufferfv(GL_COLOR,0,new float[]{1,1,1,1});glClearBufferfv(GL_COLOR,1,new float[]{1,0,0,0});
            for(int phase=0;phase<4;phase++) {
                float progress=phase==0?.02f:.5f;int query=glGenQueries();
                for(int frame=0;frame<20;frame++) {
                    if(frame==4) glBeginQuery(GL_TIME_ELAPSED,query);
                    solution.bind();near.use();nearMaterial(near,opaque,surface,disk,phase,progress,H/2);draw();
                    bloom.use();nearMaterial(bloom,opaque,surface,disk,phase,progress,H);bloom.pair("FullSize",W,H);bloom.texture("FrontTexture",0,solution.textures[0]);bloom.texture("BackTexture",1,solution.textures[1]);bloom.texture("Transmission",2,surface.textures[0]);bloom.texture("LensTexture",3,solution.textures[2]);pyramid.render(bloom,1,vao);
                    result.bind();composite.use();nearMaterial(composite,opaque,surface,disk,phase,progress,H);composite.texture("Scene",0,scene.textures[0]);composite.texture("Opaque",1,opaque.textures[0]);composite.texture("Transmission",2,surface.textures[0]);composite.texture("FrontTexture",3,solution.textures[0]);composite.texture("BackTexture",4,solution.textures[1]);composite.texture("LensTexture",5,solution.textures[2]);composite.texture("MetaTexture",6,solution.textures[3]);composite.texture("Bloom",7,pyramid.result().textures[0]);composite.integer("DisplaySpace",1);composite.integer("LayerMode",0);draw();
                }
                glEndQuery(GL_TIME_ELAPSED);report.append(String.format(Locale.ROOT,"near phase=%d solve+bloom+composite %.3f ms/frame%n",phase,glGetQueryObjectui64(query,GL_QUERY_RESULT)/16e6));glDeleteQueries(query);image(result,"near-phase-"+phase);
            }
            // Full wall at eight blocks must stop all direct singularity emission.
            var proj=new Matrix4f().perspective((float)Math.toRadians(70),W/(float)H,.05f,4096);var p=proj.transform(new Vector4f(0,0,-8,1));float wall=p.z/p.w*.5f+.5f;
            opaque.bind();glClearBufferfv(GL_COLOR,1,new float[]{wall,0,0,0});solution.bind();near.use();nearMaterial(near,opaque,surface,disk,1,.5f,H/2);draw();float[] blocked=pixels(solution);double sum=0;for(int i=0;i<blocked.length;i+=4)sum+=blocked[i]+blocked[i+1]+blocked[i+2];check(sum==0,"wall blocks near emission");
            opaque.bind();glClearBufferfv(GL_COLOR,1,new float[]{1,0,0,0});surface.bind();glClearBufferfv(GL_COLOR,1,new float[]{wall,0,0,0});solution.bind();near.use();nearMaterial(near,opaque,surface,disk,0,.02f,H/2);draw();blocked=pixels(solution);sum=0;for(int i=0;i<blocked.length;i+=4)sum+=blocked[i]+blocked[i+1]+blocked[i+2];check(sum==0,"glass in front moves light to back segment");
            solution.bind();glReadBuffer(GL_COLOR_ATTACHMENT1);var data=BufferUtils.createFloatBuffer(solution.width*solution.height*4);glReadPixels(0,0,solution.width,solution.height,GL_RGBA,GL_FLOAT,data);double back=0;for(int i=0;i<data.limit();i+=4)back+=data.get(i)+data.get(i+1)+data.get(i+2);check(back>0,"glass retains transmitted singularity radiation");
        }
    }

    static void geometryCases(GlProgram program,GlTarget result)throws Exception {
        int positions=glGenTextures(),shapes=glGenTextures();
        try(var opaque=new GlTarget(GL_R32F);var surface=new GlTarget(GL_R32F)) {
            opaque.resize(W,H);surface.resize(W,H);opaque.bind();glClearBufferfv(GL_COLOR,0,new float[]{1,0,0,0});surface.bind();glClearBufferfv(GL_COLOR,0,new float[]{1,0,0,0});
            var centers=BufferUtils.createFloatBuffer(9*4);var boxes=BufferUtils.createFloatBuffer(9*8);
            for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++){centers.put(x*2).put(y*2).put(4).put(0);boxes.put(x*2).put(y*2).put(4).put(0).put(.5f).put(.5f).put(x==0?.0625f:.5f).put(0);}centers.flip();boxes.flip();
            glBindTexture(GL_TEXTURE_2D,positions);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,9,1,0,GL_RGBA,GL_FLOAT,centers);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            glBindTexture(GL_TEXTURE_2D,shapes);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,2,9,0,GL_RGBA,GL_FLOAT,boxes);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            for(int mode=0;mode<2;mode++) {
                result.bind();GlState.fullscreen();glClearBufferfv(GL_COLOR,0,new float[]{0,0,0,1});program.use();uniforms(program,2,mode==0?.72f:.95f,800,new Matrix4f());
                program.integer("Mode",mode);program.integer("GlassCount",9);program.integer("GlassFragments",27);program.scalar("Visibility",1);program.pair("ScreenSize",W,H);
                program.texture("Positions",0,positions);program.texture("Shapes",1,shapes);program.texture("OpaqueDepth",8,opaque.textures[0]);program.texture("SurfaceDepth",9,surface.textures[0]);
                glBindVertexArray(vao);glDrawArraysInstanced(GL_TRIANGLES,0,mode==0?36:6,mode==0?9:27);float[] data=pixels(result);double sum=0;for(int i=0;i<data.length;i+=4)sum+=data[i]+data[i+1]+data[i+2];check(sum>0,"instanced glass emission "+mode);image(result,mode==0?"glass-cracks":"glass-fragments");
            }
        } finally {glDeleteTextures(positions);glDeleteTextures(shapes);}
    }
}
