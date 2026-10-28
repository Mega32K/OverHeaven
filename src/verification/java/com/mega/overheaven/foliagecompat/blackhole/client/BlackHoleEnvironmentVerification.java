package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;

/** Synthetic late-atmosphere layer and interior-camera regressions; never launches Minecraft. */
public final class BlackHoleEnvironmentVerification {
    private static final int W=321,H=181;
    private static int checks,vao;
    private static Path resources;
    private static void check(boolean condition,String label){if(!condition)throw new AssertionError(label);checks++;}
    private static String source(String name)throws Exception{return VerificationShaders.read(resources,name);}
    private static GlProgram program(String name)throws Exception{return new GlProgram(source("fullscreen.vsh"),source(name+".fsh"));}
    private static void draw(){glBindVertexArray(vao);glDrawArrays(GL_TRIANGLES,0,3);}
    private static Matrix4f projection(){return new Matrix4f().perspective((float)Math.toRadians(70),W/(float)H,.05f,4096);}
    private static void material(GlProgram p,Matrix4f vp,GlTarget opaque,GlTarget surface,GlDiskTexture disk,int steps){
        Matrix4f inverse=new Matrix4f(vp).invert();Vector4f eye=inverse.transform(new Vector4f(0,0,1,0));eye.div(eye.w);
        p.matrix("VP",vp);p.matrix("InverseVP",inverse);p.vector("Eye",eye.x,eye.y,eye.z);p.vector("Center",0,-7,0);
        p.vector("DiskNormal",0,1,0);p.vector("DiskRight",1,0,0);p.vector("DiskForward",0,0,-1);
        p.vector("HotColor",1,.98f,.94f);p.vector("ColdColor",.6f,.46f,.36f);
        p.scalar("Radius",3.2);p.scalar("Inner",3.8);p.scalar("Outer",11);p.scalar("Thickness",.12);
        p.scalar("Brightness",0);p.scalar("Glow",0);p.scalar("Rotation",.25);p.scalar("Infall",.12);p.scalar("Lensing",1);
        p.scalar("Time",7);p.scalar("Seed",12);p.integer("Steps",steps);p.scalar("PixelAngle",2.0/(H*projection().m11()));
        p.texture("OpaqueDepth",8,opaque.textures[1]);p.texture("SurfaceDepth",9,surface.textures[1]);p.texture("DiskTexture",10,disk.handle());
    }
    private static float[] read(GlTarget target,int attachment){target.bind();glReadBuffer(GL_COLOR_ATTACHMENT0+attachment);var b=BufferUtils.createFloatBuffer(target.width*target.height*4);glReadPixels(0,0,target.width,target.height,GL_RGBA,GL_FLOAT,b);float[] a=new float[b.remaining()];b.get(a);return a;}
    private static void image(float[] data,Path file)throws Exception{var im=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);for(int y=0;y<H;y++)for(int x=0;x<W;x++){int at=(y*W+x)*4,rgb=0;for(int c=0;c<3;c++)rgb|=Math.max(0,Math.min(255,Math.round(data[at+c]*255)))<<(16-c*8);im.setRGB(x,H-y-1,rgb);}ImageIO.write(im,"PNG",file.toFile());}
    private static float[] center(GlTarget target){float[] all=read(target,0);int i=((H/2)*W+W/2)*4;return new float[]{all[i],all[i+1],all[i+2],all[i+3]};}
    private static void bindComposite(GlProgram p,GlTarget scene,GlTarget opaque,GlTarget surface,GlTarget solution,GlTarget bloom){
        p.texture("Scene",0,scene.textures[0]);p.texture("Opaque",1,opaque.textures[0]);p.texture("Transmission",2,surface.textures[0]);
        p.texture("FrontTexture",3,solution.textures[0]);p.texture("BackTexture",4,solution.textures[1]);p.texture("LensTexture",5,solution.textures[2]);p.texture("MetaTexture",6,solution.textures[3]);p.texture("Bloom",7,bloom.textures[0]);p.integer("DisplaySpace",0);p.integer("LayerMode",0);
    }
    public static void main(String[] args)throws Exception{
        resources=Path.of(args[0]);Path output=Path.of(args[1]);Files.createDirectories(output);
        check(glfwInit(),"GLFW init");glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
        long window=glfwCreateWindow(W,H,"environment verification",0,0);check(window!=0,"hidden context");glfwMakeContextCurrent(window);GL.createCapabilities();vao=glGenVertexArrays();
        String fillSource="""
            #version 330 core
            in vec2 texCoord;
            layout(location=0) out vec4 Color;
            layout(location=1) out float Depth;
            uniform int Late;
            void main(){Depth=1.0;Color=Late==0?vec4(.05,.1,.2,1):vec4(texCoord.x,texCoord.y,.3,1);}
            """;
        String fixedSource="""
            #version 330 core
            layout(location=0) out vec4 Front;
            layout(location=1) out vec4 Back;
            layout(location=2) out vec4 Lens;
            layout(location=3) out vec4 Meta;
            void main(){Front=Back=vec4(0,0,0,1);Lens=vec4(.7,.7,65504,65504);Meta=vec4(1000000,1000000,0,2);}
            """;
        try(var integrate=program("integrate");var composite=program("composite");var fill=new GlProgram(source("fullscreen.vsh"),fillSource);var fixed=new GlProgram(source("fullscreen.vsh"),fixedSource);
            var opaque=new GlTarget(GL_RGBA16F,GL_R32F);var surface=new GlTarget(GL_RGBA16F,GL_R32F);var scene=new GlTarget(GL_RGBA16F);var result=new GlTarget(GL_RGBA16F);
            var solution=new GlTarget(GL_RGBA16F,GL_RGBA16F,GL_RGBA32F,GL_RGBA32F);var bloom=new GlTarget(GL_RGBA16F);var stream=Files.newInputStream(resources.resolve("../../textures/blackhole/disk_detail.png"));var disk=new GlDiskTexture(stream)){
            GlState.fullscreen();for(var t:new GlTarget[]{opaque,surface,scene,result,solution,bloom})t.resize(W,H);
            surface.bind();glClearBufferfv(GL_COLOR,0,new float[]{1,1,1,1});glClearBufferfv(GL_COLOR,1,new float[]{1,0,0,0});bloom.bind();glClearBufferfv(GL_COLOR,0,new float[]{0,0,0,1});
            opaque.bind();fill.use();fill.integer("Late",0);draw();scene.bind();fill.integer("Late",1);draw();image(read(scene,0),output.resolve("late-input.png"));
            solution.bind();fixed.use();draw();result.bind();composite.use();material(composite,projection(),opaque,surface,disk,48);bindComposite(composite,scene,opaque,surface,solution,bloom);draw();
            image(read(result,0),output.resolve("late-lensed.png"));float[] late=center(result);
            check(Math.abs(late[0]-.7)<.015 && Math.abs(late[1]-.7)<.015,"late sky/cloud color must move with lens, not stay at original UV: "+java.util.Arrays.toString(late));
            if(args.length>2){
                Path old=Path.of(args[2]);
                String oldFragment=Files.readString(old.resolve("composite.fsh")).replace("#include \"field.glsl\"",Files.readString(old.resolve("field.glsl")));
                try(var oldComposite=new GlProgram(source("fullscreen.vsh"),oldFragment)){
                    result.bind();oldComposite.use();material(oldComposite,projection(),opaque,surface,disk,48);bindComposite(oldComposite,scene,opaque,surface,solution,bloom);draw();
                    float[] before=center(result);check(Math.abs(before[0]-.5)<.015 && Math.abs(before[1]-.5)<.015,"old shader reproduces stationary late atmosphere");
                    image(read(result,0),output.resolve("late-before.png"));System.out.println("Confirmed old late-atmosphere defect: expected warped .7/.7, old="+before[0]+"/"+before[1]+", new="+late[0]+"/"+late[1]);
                }
            }
            // Oculus pre-refraction background mode shares the complete nontransparent scene.
            result.bind();composite.use();material(composite,projection(),opaque,surface,disk,48);bindComposite(composite,scene,opaque,surface,solution,bloom);composite.integer("LayerMode",1);draw();late=center(result);check(Math.abs(late[0]-.7)<.015,"Oculus back layer must include late background");
            // A transparent source cannot be imported from completed color without its layer data.
            surface.bind();glEnable(GL_SCISSOR_TEST);glScissor((int)(W*.65),(int)(H*.65),W/8,H/8);glClearBufferfv(GL_COLOR,1,new float[]{.5f,0,0,0});glDisable(GL_SCISSOR_TEST);
            result.bind();composite.use();composite.integer("LayerMode",0);draw();late=center(result);check(Math.abs(late[0]-.5)<.015,"unresolved transparent source preserves destination");
            surface.bind();glClearBufferfv(GL_COLOR,1,new float[]{1,0,0,0});
            // No lens: completed sky is preserved, without early-color subtraction residue.
            var bounds=new BlackHoleScreenBounds();
            for(int pitch:new int[]{-89,-60,-30,0,30,60,89}){
                Matrix4f vp=projection().mul(new Matrix4f().rotateZ(.12f).rotateX((float)Math.toRadians(pitch)).rotateY(.3f));
                Vector4f eye=new Matrix4f(vp).invert().transform(new Vector4f(0,0,1,0));eye.div(eye.w);
                check(bounds.project(vp,eye,0,-7,0,15.09,W,H) && bounds.left==0 && bounds.bottom==0 && bounds.right==W && bounds.top==H,"inside optical eye must retain full viewport pitch="+pitch);
                float[] previous=null;int coverage=0,moved=0;
                for(int steps:new int[]{24,48,96}){
                    solution.bind();integrate.use();material(integrate,vp,opaque,surface,disk,steps);draw();float[] map=read(solution,2),meta=read(solution,3);coverage=0;moved=0;
                    for(int y=0;y<H;y++)for(int x=0;x<W;x++){
                        int i=(y*W+x)*4;check(Float.isFinite(map[i])&&Float.isFinite(map[i+1]),"finite interior UV");
                        if(meta[i+3]>=0 && map[i]>=0 && map[i]<=1 && map[i+1]>=0 && map[i+1]<=1){coverage++;if(Math.hypot(map[i]-(x+.5)/W,map[i+1]-(y+.5)/H)>.001)moved++;}
                        if(previous!=null)check(Math.abs(map[i]-previous[i])<.000003 && Math.abs(map[i+1]-previous[i+1])<.000003,"lens must not change with material step budget");
                    }
                    check(coverage>W*H*.25 && moved>W*H*.15,"interior camera must retain visible lens coverage pitch="+pitch+", coverage="+coverage+", moved="+moved);previous=map;
                    if(steps==48){result.bind();composite.use();material(composite,vp,opaque,surface,disk,steps);bindComposite(composite,scene,opaque,surface,solution,bloom);draw();image(read(result,0),output.resolve("interior-pitch-"+pitch+".png"));}
                }
                System.out.println("Interior pitch="+pitch+", verified on-screen UV="+coverage+", nonidentity="+moved+" / "+(W*H));
                solution.bind();integrate.use();material(integrate,vp,opaque,surface,disk,48);integrate.scalar("Lensing",0);draw();float[] zero=read(solution,3);for(int i=0;i<zero.length;i+=4)check(zero[i+3]<0,"zero lens disables background mapping");
            }
            // Complete foreground wall before the influence field is unchanged.
            Vector4f wall=projection().transform(new Vector4f(0,0,-.2f,1));float wallDepth=wall.z/wall.w*.5f+.5f;
            opaque.bind();glClearBufferfv(GL_COLOR,1,new float[]{wallDepth,0,0,0});surface.bind();glClearBufferfv(GL_COLOR,1,new float[]{wallDepth,0,0,0});
            solution.bind();integrate.use();material(integrate,projection(),opaque,surface,disk,48);integrate.vector("Center",0,0,-30);draw();
            result.bind();composite.use();material(composite,projection(),opaque,surface,disk,48);composite.vector("Center",0,0,-30);bindComposite(composite,scene,opaque,surface,solution,bloom);draw();
            float[] protectedWall=read(result,0),originalWall=read(scene,0);for(int i=0;i<protectedWall.length;i++)check(Math.abs(protectedWall[i]-originalWall[i])<.002,"front wall remains unchanged");
            check(!bounds.project(projection(),new Vector4f(0,0,0,1),0,0,40,2,W,H),"outside behind-eye object remains culled");
            check(glGetError()==GL_NO_ERROR,"no GL error");
        }finally{glDeleteVertexArrays(vao);glfwDestroyWindow(window);glfwTerminate();}
        System.out.println("PASS: "+checks+" environment layer/interior camera checks (synthetic GPU, not Minecraft).");
    }
}
