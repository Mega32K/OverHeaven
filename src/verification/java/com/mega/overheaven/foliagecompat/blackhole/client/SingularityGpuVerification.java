package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static com.mega.overheaven.foliagecompat.blackhole.client.FinaleGpuVerification.*;
import java.nio.file.*;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL;

/** Production GLSL and source-switch lifecycle in a hidden context. No Minecraft is started. */
public final class SingularityGpuVerification {
    static final StringBuilder REPORT=new StringBuilder();
    static double energy(float[] values) {
        double sum=0; for(int i=0;i<values.length;i+=4) for(int c=0;c<3;c++) { if(!Float.isFinite(values[i+c]))throw new AssertionError("nonfinite radiance");sum+=values[i+c]; }
        return sum;
    }
    static void assertion(boolean pass,String text) { check(pass,text);REPORT.append("PASS ").append(text).append('\n'); }
    public static void main(String[] args) throws Exception {
        source=Path.of(args[0]);output=Path.of(args[1]);Files.createDirectories(output);Path pack=Path.of(args[2]);
        String vertex=read("fullscreen.vsh"),adapter=read("finale_pack_stars.fsh");
        String host=PackSkySource.load(pack,Map.of(),adapter);
        assertion(host.contains("float GetStarNoise") && !host.contains("hash3("),"active pack supplies stars, not the mod generator");
        try { PackSkySource.load(pack,Map.of("NIGHT_STAR_AMOUNT","2\n#error invalid"),adapter);throw new AssertionError("option injection"); } catch(java.io.IOException expected) { assertion(true,"invalid option rejected"); }
        check(glfwInit(),"GLFW");glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);long window=glfwCreateWindow(64,64,"singularity verification",0,0);check(window!=0,"context");glfwMakeContextCurrent(window);GL.createCapabilities();vao=glGenVertexArrays();
        REPORT.append("GPU: ").append(glGetString(GL_RENDERER)).append("; synthetic 1920x1080, 960x540 solve\n");
        try(var stars=new GlProgram(vertex,host);var sky=new GlTarget(GL_RGBA16F).linear();var lifecycle=new FinaleSky()) {
            GlState.fullscreen();sky.resize(W/2,H/2);
            float[] angles={0,1.55f,-1.55f};String[] names={"horizon","lower-hemisphere","upper-hemisphere"};
            for(int i=0;i<angles.length;i++) {
                sky.bind();stars.use();uniforms(stars,4,.9f,0,new Matrix4f().rotateX(angles[i]));draw();
                double sum=energy(pixels(sky));assertion(sum>10,"pack sky visible "+names[i]);image(sky,"pack-"+names[i]);REPORT.append(names[i]).append(" energy=").append(sum).append('\n');
            }
            double previous=Double.MAX_VALUE;
            for(float extinction:new float[]{0,.4f,.74f,.89f,1}) {
                sky.bind();stars.use();uniforms(stars,5,extinction,1800,new Matrix4f());stars.scalar("OrbitAngle",0);stars.integer("StarsOnly",1);draw();double sum=energy(pixels(sky));
                assertion(sum<=previous*1.1,"pack extinction decreases "+extinction);previous=sum;
            }
            assertion(previous==0,"pack stars fully extinguish");
            try(var disabled=new GlProgram(vertex,PackSkySource.load(pack,Map.of("NIGHT_STAR_AMOUNT","0"),adapter))) {
                sky.bind();disabled.use();uniforms(disabled,4,.9f,1600,new Matrix4f());disabled.integer("StarsOnly",1);draw();assertion(energy(pixels(sky))==0,"active NIGHT_STAR_AMOUNT=0 respected");
                disabled.integer("StarsOnly",0);draw();assertion(energy(pixels(sky))>0,"cosmic gas remains independent of host star amount");
            }
            sky.bind();stars.use();uniforms(stars,6,.3f,2208,new Matrix4f());stars.scalar("Radius",3.2);stars.scalar("Glow",.7);
            stars.vector("DiskRight",1,0,0);stars.vector("DiskNormal",0,.92f,.392f);stars.vector("DiskForward",0,.392f,-.92f);draw();
            assertion(energy(pixels(sky))==0,"pack star background is extinguished before exact black");image(sky,"pack-silence-background");
            stars.integer("Black",1);draw();assertion(energy(pixels(sky))==0,"final silence stays exactly black including remnant");
            stars.scalar("Radius",0);stars.scalar("Glow",0);
            Object key=new Object();int[] calls={0,0};
            FinaleSky.Source packSource=()->{calls[0]++;return host;},ownSource=()->{calls[1]++;return read("finale_stars.fsh");};
            var program=lifecycle.select(false,null,()->vertex,packSource,ownSource);
            assertion(calls[0]==0 && calls[1]==1,"shaders off uses only own stars");
            assertion(lifecycle.select(false,null,()->vertex,packSource,ownSource)==program,"own program reused");
            program=lifecycle.select(true,key,()->vertex,packSource,ownSource);
            assertion(calls[0]==1 && calls[1]==1,"shaders on uses only pack stars");
            assertion(lifecycle.select(true,key,()->vertex,packSource,ownSource)==program && calls[0]==1,"pack read once per identity");
            int released=program.handle();lifecycle.close();assertion(!glIsProgram(released),"program released");
            var failed=lifecycle.select(true,new Object(),()->vertex,()->{throw new java.io.IOException("expected unsupported-pack test");},ownSource);
            assertion(failed==null && calls[1]==1,"unsupported pack never falls back to own stars");
            program=lifecycle.select(false,null,()->vertex,packSource,ownSource);assertion(program!=null && calls[1]==2,"shaders off recovers after unsupported pack");
            int query=glGenQueries();for(int i=0;i<36;i++){if(i==6)glBeginQuery(GL_TIME_ELAPSED,query);sky.bind();stars.use();uniforms(stars,5,.3f,1800,new Matrix4f());draw();}glEndQuery(GL_TIME_ELAPSED);REPORT.append(String.format(Locale.ROOT,"pack sky only: %.3f ms/frame%n",glGetQueryObjectui64(query,GL_QUERY_RESULT)/30e6));glDeleteQueries(query);
            nearTests(vertex);
            assertion(glGetError()==GL_NO_ERROR,"GL state error-free");
            Files.writeString(output.resolve("report.txt"),REPORT);System.out.println(REPORT);
        } finally {glDeleteVertexArrays(vao);glfwDestroyWindow(window);glfwTerminate();}
    }
    static void nearTests(String vertex) throws Exception {
        try(var near=new GlProgram(vertex,read("integrate.fsh"));var compose=new GlProgram(vertex,read("composite.fsh"));var bloom=new GlProgram(vertex,read("bloom.fsh"));
            var opaque=new GlTarget(GL_RGBA16F,GL_R32F);var surface=new GlTarget(GL_RGBA16F,GL_R32F);var scene=new GlTarget(GL_RGBA16F);var result=new GlTarget(GL_RGBA16F);
            var solution=new GlTarget(GL_RGBA16F,GL_RGBA16F,GL_RGBA32F,GL_RGBA32F);var pyramid=new GlBloom();var input=Files.newInputStream(source.resolve("../../textures/blackhole/disk_detail.png"));var disk=new GlDiskTexture(input)) {
            opaque.resize(W,H);surface.resize(W,H);scene.resize(W,H);result.resize(W,H);solution.resize(W/2,H/2);pyramid.resize(W,H,48);
            scene.bind();glClearBufferfv(GL_COLOR,0,new float[]{0,0,0,1});opaque.bind();glClearBufferfv(GL_COLOR,0,new float[]{0,0,0,1});glClearBufferfv(GL_COLOR,1,new float[]{1,0,0,0});surface.bind();glClearBufferfv(GL_COLOR,0,new float[]{1,1,1,1});glClearBufferfv(GL_COLOR,1,new float[]{1,0,0,0});
            double base=0;
            for(int steps:new int[]{96,48,24}) {
                solution.bind();near.use();nearMaterial(near,opaque,surface,disk,1,.5f,H/2);near.integer("Steps",steps);draw();
                double value=energy(pixels(solution));if(base==0)base=value;assertion(Math.abs(value/base-1)<.15,"24/48/96 radiance converges at "+steps);REPORT.append("near energy ").append(steps).append('=').append(value).append('\n');
            }
            double last=0;
            for(float glow:new float[]{0,1,2}) {
                solution.bind();near.use();nearMaterial(near,opaque,surface,disk,1,.5f,H/2);draw();
                bloom.use();nearMaterial(bloom,opaque,surface,disk,1,.5f,H);bloom.pair("FullSize",W,H);bloom.texture("FrontTexture",0,solution.textures[0]);bloom.texture("BackTexture",1,solution.textures[1]);bloom.texture("Transmission",2,surface.textures[0]);bloom.texture("LensTexture",3,solution.textures[2]);pyramid.render(bloom,1,vao);
                result.bind();compose.use();nearMaterial(compose,opaque,surface,disk,1,.5f,H);compose.scalar("Glow",glow);compose.texture("Scene",0,scene.textures[0]);compose.texture("Opaque",1,opaque.textures[0]);compose.texture("Transmission",2,surface.textures[0]);compose.texture("FrontTexture",3,solution.textures[0]);compose.texture("BackTexture",4,solution.textures[1]);compose.texture("LensTexture",5,solution.textures[2]);compose.texture("MetaTexture",6,solution.textures[3]);compose.texture("Bloom",7,pyramid.result().textures[0]);compose.integer("DisplaySpace",1);compose.integer("LayerMode",0);draw();
                double value=energy(pixels(result));assertion(value>last,"glow increases output "+glow);last=value;image(result,"singularity-glow-"+(int)glow);
            }
            // Every new membrane uses real depth. A wall in front of the whole volume rejects it.
            var projection=new Matrix4f().perspective((float)Math.toRadians(70),W/(float)H,.05f,4096);var point=projection.transform(new Vector4f(0,0,-8,1));float wall=point.z/point.w*.5f+.5f;
            opaque.bind();glClearBufferfv(GL_COLOR,1,new float[]{wall,0,0,0});solution.bind();near.use();nearMaterial(near,opaque,surface,disk,1,.5f,H/2);draw();assertion(energy(pixels(solution))==0,"wall rejects all new membranes");
            opaque.bind();glClearBufferfv(GL_COLOR,1,new float[]{1,0,0,0});surface.bind();glClearBufferfv(GL_COLOR,1,new float[]{wall,0,0,0});solution.bind();near.use();nearMaterial(near,opaque,surface,disk,1,.5f,H/2);draw();assertion(energy(pixels(solution))==0,"glass before singularity leaves front segment empty");
            surface.bind();glClearBufferfv(GL_COLOR,1,new float[]{1,0,0,0});
            for(int i=0;i<4;i++) {
                float yaw=i==1?1.57f:i==2?3.14f:0,distance=i==3?1:24;
                solution.bind();near.use();nearMaterial(near,opaque,surface,disk,1,.5f,H/2);uniforms(near,1,.5f,340,new Matrix4f().rotateY(yaw));near.vector("Center",(float)Math.sin(yaw)*distance,0,-(float)Math.cos(yaw)*distance);draw();
                assertion(energy(pixels(solution))>0,"world-space front/side/back/inside "+i);image(solution,"membrane-view-"+i);
                float[] frozen=pixels(solution);draw();assertion(Arrays.equals(frozen,pixels(solution)),"paused time is stable "+i);
            }
        }
    }
}
