package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;

/** Near-camera regression fixtures, using the production integration and composition shaders. */
public final class BlackHoleLensVerification {
    private static final int WIDTH=641, HEIGHT=359;
    private static final Matrix4f VP=new Matrix4f().perspective((float)Math.toRadians(70),WIDTH/(float)HEIGHT,0.05f,4096);
    private static final Matrix4f INVERSE=new Matrix4f(VP).invert();
    private static int checks, vao;
    private static Path resources;
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); checks++; }
    private static String source(String file) throws Exception {
        return VerificationShaders.read(resources,file);
    }
    private static GlProgram program(String name) throws Exception { return new GlProgram(source("fullscreen.vsh"),source(name+".fsh")); }
    private static void draw() { glBindVertexArray(vao); glDrawArrays(GL_TRIANGLES,0,3); }
    private static float depth(float distance) { var point=VP.transform(new org.joml.Vector4f(0,0,-distance,1)); return point.z/point.w*0.5f+0.5f; }
    private static void parameters(GlProgram program,GlTarget opaque,GlTarget surface,GlDiskTexture detail,float x,float y,float z,int steps,int height) {
        program.matrix("VP",VP); program.matrix("InverseVP",INVERSE); program.vector("Eye",0,0,0); program.vector("Center",x,y,z);
        program.vector("DiskNormal",0,0.8660254f,0.5f); program.vector("DiskRight",1,0,0); program.vector("DiskForward",0,-0.5f,0.8660254f);
        program.vector("HotColor",1,0.98f,0.94f); program.vector("ColdColor",0.6f,0.46f,0.36f);
        program.scalar("Radius",3.2); program.scalar("Inner",3.8); program.scalar("Outer",11); program.scalar("Thickness",0.12);
        program.scalar("Brightness",0); program.scalar("Rotation",0.25); program.scalar("Infall",0.12); program.scalar("Lensing",1);
        program.scalar("Time",7); program.scalar("Seed",12); program.integer("Steps",steps); program.scalar("PixelAngle",2.0/(height*VP.m11()));
        program.texture("OpaqueDepth",8,opaque.textures[1]); program.texture("SurfaceDepth",9,surface.textures[1]); program.texture("DiskTexture",10,detail.handle());
    }
    private static void compose(GlProgram composite,GlTarget output,GlTarget opaque,GlTarget surface,GlTarget solution,GlTarget bloom,GlDiskTexture detail,float x,float y,float z,int steps) {
        output.bind(); composite.use(); parameters(composite,opaque,surface,detail,x,y,z,steps,HEIGHT);
        composite.texture("Scene",0,opaque.textures[0]); composite.texture("Opaque",1,opaque.textures[0]); composite.texture("Transmission",2,surface.textures[0]);
        composite.texture("FrontTexture",3,solution.textures[0]); composite.texture("BackTexture",4,solution.textures[1]);
        composite.texture("LensTexture",5,solution.textures[2]); composite.texture("MetaTexture",6,solution.textures[3]); composite.texture("Bloom",7,bloom.textures[0]);
        composite.integer("DisplaySpace",0); composite.integer("LayerMode",0); composite.scalar("Glow",0); draw();
    }
    private static float[] pixels(GlTarget target) {
        target.bind(); var buffer=BufferUtils.createFloatBuffer(target.width*target.height*4);
        glReadBuffer(GL_COLOR_ATTACHMENT0); glReadPixels(0,0,target.width,target.height,GL_RGBA,GL_FLOAT,buffer);
        float[] values=new float[buffer.remaining()]; buffer.get(values); return values;
    }
    private static void image(float[] values,Path file) throws Exception {
        var image=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<HEIGHT;y++) for(int x=0;x<WIDTH;x++) {
            int rgb=0, at=(y*WIDTH+x)*4;
            for(int channel=0;channel<3;channel++) rgb|=Math.max(0,Math.min(255,Math.round(values[at+channel]*255)))<<(16-channel*8);
            image.setRGB(x,HEIGHT-1-y,rgb);
        }
        ImageIO.write(image,"PNG",file.toFile());
    }
    private static float[] center(GlTarget target) { float[] value=new float[4]; target.bind(); glReadBuffer(GL_COLOR_ATTACHMENT0); glReadPixels(WIDTH/2,HEIGHT/2,1,1,GL_RGBA,GL_FLOAT,value); return value; }
    public static void main(String[] arguments) throws Exception {
        resources=Path.of(arguments[0]); Path output=Path.of(arguments[1]); Files.createDirectories(output);
        check(glfwInit(),"GLFW init"); glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE); glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3); glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        long window=glfwCreateWindow(64,64,"OverHeaven near-camera verification",0,0); check(window!=0,"hidden context"); glfwMakeContextCurrent(window); GL.createCapabilities(); vao=glGenVertexArrays();
        System.out.println("Near-camera GPU: "+glGetString(GL_RENDERER)+" / "+glGetString(GL_VERSION));
        String fixture="""
            #version 330 core
            in vec2 texCoord;
            layout(location=0) out vec4 Color;
            layout(location=1) out float Depth;
            uniform int Mode;
            uniform float NearDepth;
            void main() {
                float grid=mod(floor(texCoord.x*32.0)+floor(texCoord.y*18.0),2.0);
                Color=vec4(0.0,0.15+0.5*grid,0.4+0.3*texCoord.y,1.0); Depth=1.0;
                if(Mode==1) {
                    Color=texCoord.x<0.04?vec4(0,0,1,1):(texCoord.x>0.45 && texCoord.x<0.55?vec4(1,0,0,1):vec4(0,1,0,1));
                } else if(Mode==2) {
                    Color=texCoord.x<0.4?vec4(1,0,0,1):(texCoord.x>0.6?vec4(0,0,1,1):vec4(0,1,0,1));
                } else if(texCoord.x>0.72 && texCoord.y<0.28) { Color=vec4(1,0,0,1); Depth=NearDepth; }
            }
            """;
        String controlled="""
            #version 330 core
            layout(location=0) out vec4 Front;
            layout(location=1) out vec4 Back;
            layout(location=2) out vec4 Lens;
            layout(location=3) out vec4 Meta;
            uniform vec2 Warp;
            uniform float Expected;
            uniform int InvalidNeighbor;
            void main() {
                Front=vec4(0,0,0,1); Back=Front; Lens=vec4(Warp,65504.0,65504.0); Meta=vec4(1000000.0,1000000.0,0.0,Expected);
                if(InvalidNeighbor==1 && gl_FragCoord.x<1.0) { Lens.xy=vec2(0.5); Meta.w=-1.0; }
                if(InvalidNeighbor==2) Lens.xy=vec2(gl_FragCoord.x<1.0?0.2:0.8,0.5);
                if(InvalidNeighbor==3) Lens.xy=vec2(gl_FragCoord.x<1.0?0.499:0.501,0.5);
            }
            """;
        try(var stream=Files.newInputStream(resources.resolve("../../textures/blackhole/disk_detail.png").normalize()); var detail=new GlDiskTexture(stream);
            var fill=new GlProgram(source("fullscreen.vsh"),fixture); var fixed=new GlProgram(source("fullscreen.vsh"),controlled);
            var integrate=program("integrate"); var composite=program("composite");
            var opaque=new GlTarget(GL_RGBA16F,GL_R32F); var surface=new GlTarget(GL_RGBA16F,GL_R32F);
            var solution=new GlTarget(GL_RGBA16F,GL_RGBA16F,GL_RGBA32F,GL_RGBA32F); var bloom=new GlTarget(GL_RGBA16F); var result=new GlTarget(GL_RGBA16F)) {
            GlState.fullscreen(); opaque.resize(WIDTH,HEIGHT); surface.resize(WIDTH,HEIGHT); bloom.resize(1,1); result.resize(WIDTH,HEIGHT);
            surface.bind(); glClearBufferfv(GL_COLOR,0,new float[]{1,1,1,1}); glClearBufferfv(GL_COLOR,1,new float[]{1,0,0,0});
            bloom.bind(); glClearBufferfv(GL_COLOR,0,new float[]{0,0,0,1});
            opaque.bind(); fill.use(); fill.integer("Mode",0); fill.scalar("NearDepth",depth(0.2f)); draw();
            image(pixels(opaque),output.resolve("input.png"));
            int copies=0;
            float[][] centers={{0,-7,-6},{7,-4,-3},{0,-6,3},{0,-3,-14},{0,0,-1}};
            for(int scene=0;scene<centers.length;scene++) for(int steps:new int[]{24,48,96}) {
                float scale=steps==24?0.375f:steps==48?0.5f:0.75f;
                solution.resize((int)Math.ceil(WIDTH*scale),(int)Math.ceil(HEIGHT*scale));
                float[] c=centers[scene]; solution.bind(); integrate.use(); parameters(integrate,opaque,surface,detail,c[0],c[1],c[2],steps,solution.height); draw();
                compose(composite,result,opaque,surface,solution,bloom,detail,c[0],c[1],c[2],steps);
                float[] values=pixels(result); int caseCopies=0;
                for(int y=0;y<HEIGHT;y++) for(int x=0;x<WIDTH;x++) {
                    int at=(y*WIDTH+x)*4;
                    for(int channel=0;channel<4;channel++) check(Float.isFinite(values[at+channel]),"finite close/offscreen/core field "+scene+" steps="+steps);
                    boolean originalHand=(x+0.5f)/WIDTH>0.72f && (y+0.5f)/HEIGHT<0.28f;
                    if(!originalHand && values[at]>0.003f) caseCopies++;
                }
                if(steps==48) image(values,output.resolve("near-"+scene+".png"));
                System.out.println("Near-camera fixture "+scene+", steps="+steps+", copied foreground pixels="+caseCopies);
                copies+=caseCopies;
            }
            check(copies==0,"near-camera foreground source must not be duplicated; copies="+copies);
            // Directly exercise the production composite at an inside-sphere eye position.
            solution.resize(2,1); opaque.bind(); fill.use(); fill.integer("Mode",1); draw();
            for(float edge:new float[]{-0.1f,0,0.001f,0.01f,0.02f,0.034f}) {
                solution.bind(); fixed.use(); fixed.pair("Warp",edge,0.5f); fixed.scalar("Expected",1); fixed.integer("InvalidNeighbor",0); draw();
                compose(composite,result,opaque,surface,solution,bloom,detail,0,-15,-8,48);
                float[] color=center(result);
                float margin=Math.max(0.5f/WIDTH,0.5f/HEIGHT), weight=Math.max(0,Math.min(1,(edge-margin)/(0.035f-margin))); weight=weight*weight*(3-2*weight);
                check(Math.abs(color[0]-(1-weight))<0.004f && Math.abs(color[2]-weight)<0.004f,"edge blends source colors, not a sweep of UVs "+edge);
                check(color[1]<0.003f,"no intermediate green scenery at boundary");
            }
            solution.bind(); fixed.use(); fixed.pair("Warp",0.01f,0.5f); fixed.integer("InvalidNeighbor",1); draw();
            compose(composite,result,opaque,surface,solution,bloom,detail,0,-15,-8,48);
            check(center(result)[1]<0.003f,"invalid low-resolution neighbor never pulls lens UV across scenery");
            solution.bind(); fixed.use(); fixed.integer("InvalidNeighbor",2); fixed.scalar("Expected",1); draw();
            compose(composite,result,opaque,surface,solution,bloom,detail,0,-15,-8,48);
            check(center(result)[0]<0.003f && center(result)[1]>0.99f,"discontinuous valid UVs must not invent the red intermediate source");
            image(pixels(result),output.resolve("discontinuous-valid-sources.png"));
            solution.bind(); fixed.use(); fixed.integer("InvalidNeighbor",3); draw();
            compose(composite,result,opaque,surface,solution,bloom,detail,0,-15,-8,48);
            check(center(result)[0]>0.99f,"continuous valid UV neighborhood retains interpolation");
            opaque.bind(); fill.use(); fill.integer("Mode",2); draw();
            solution.bind(); fixed.use(); fixed.integer("InvalidNeighbor",2); draw();
            compose(composite,result,opaque,surface,solution,bloom,detail,0,-15,-8,48);
            float[] selected=pixels(result);
            int leftSample=((HEIGHT/2)*WIDTH+WIDTH/2-8)*4, rightSample=((HEIGHT/2)*WIDTH+WIDTH/2+8)*4;
            check(selected[leftSample]>0.99f && selected[leftSample+1]<0.003f,"left dominant valid neighbor selected");
            check(selected[rightSample+2]>0.99f && selected[rightSample+1]<0.003f,"right dominant valid neighbor selected");
            try(var curve=new GlProgram(source("fullscreen.vsh"),"#version 330 core\nlayout(location=0) out vec4 Color;\n"+source("field.glsl")+"\nvoid main(){ Color=vec4(innerTransitionColor(vec3(1.0,0.2,0.05),mix(Radius,Inner,(gl_FragCoord.x-0.5)/4.0)),1.0); }");
                var probe=new GlTarget(GL_RGBA32F)) {
                probe.resize(5,1); probe.bind(); curve.use(); curve.scalar("Radius",3.2); curve.scalar("Inner",3.8); draw();
                float[] samples=pixels(probe); float luminance=0.2126f+0.7152f*0.2f+0.0722f*0.05f;
                float[] sourceColor={1,0.2f,0.05f};
                for(int sample=0;sample<5;sample++) {
                    float fraction=sample/4.0f, saturation=0.5f*fraction*fraction*(3-2*fraction);
                    for(int channel=0;channel<3;channel++) check(Math.abs(samples[sample*4+channel]-(luminance+(sourceColor[channel]-luminance)*saturation))<0.00001,"inner saturation curve sample "+sample);
                    float actualLuminance=samples[sample*4]*0.2126f+samples[sample*4+1]*0.7152f+samples[sample*4+2]*0.0722f;
                    check(Math.abs(actualLuminance-luminance)<0.00001,"inner saturation preserves luminance");
                }
            }
            for(float expected:new float[]{20,80}) {
                opaque.bind(); fill.use(); fill.integer("Mode",0); fill.scalar("NearDepth",depth(0.2f)); draw();
                solution.bind(); fixed.use(); fixed.pair("Warp",0.8f,0.15f); fixed.scalar("Expected",expected); fixed.integer("InvalidNeighbor",0); draw();
                compose(composite,result,opaque,surface,solution,bloom,detail,0,-15,-8,48);
                check(center(result)[0]<0.003f,"inside-sphere lens rejects a near source with a distant endpoint");
            }
            // A valid distant source must still distort the scene: this must not disable lensing.
            opaque.bind(); fill.use(); fill.scalar("NearDepth",depth(200)); draw();
            solution.bind(); fixed.use(); fixed.scalar("Expected",200); draw();
            compose(composite,result,opaque,surface,solution,bloom,detail,0,-15,-8,48);
            check(center(result)[0]>0.99f,"valid near-camera lens remains active");
            solution.bind(); fixed.use(); fixed.scalar("Expected",20); draw();
            compose(composite,result,opaque,surface,solution,bloom,detail,0,-15,-8,48);
            check(center(result)[0]>0.99f,"sky ray may lens distant terrain beyond the field exit");
            // One bad tap in the footprint is sufficient, including a texel not selected by nearest sampling.
            for(int tap=0;tap<4;tap++) {
                opaque.bind(); fill.use(); draw();
                glEnable(GL_SCISSOR_TEST); glScissor((int)Math.floor(0.8f*WIDTH-0.5f)+(tap&1),(int)Math.floor(0.15f*HEIGHT-0.5f)+(tap>>1),1,1); glClearBufferfv(GL_COLOR,1,new float[]{depth(0.2f),0,0,0}); glDisable(GL_SCISSOR_TEST);
                compose(composite,result,opaque,surface,solution,bloom,detail,0,-15,-8,48);
                check(center(result)[0]<0.003f,"bilinear depth footprint rejects foreground tap "+tap);
            }
            check(glGetError()==GL_NO_ERROR,"no GL errors");
        } finally { glDeleteVertexArrays(vao); glfwDestroyWindow(window); glfwTerminate(); }
        System.out.println("PASS: "+checks+" near-camera lens checks (includes finite per-pixel components; 15 rendered camera/quality cases plus controlled reprojection cases).");
    }
}
