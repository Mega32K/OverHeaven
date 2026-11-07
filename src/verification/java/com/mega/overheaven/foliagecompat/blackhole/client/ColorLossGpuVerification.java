package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import com.mega.overheaven.foliagecompat.colorloss.ColorLossProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;

/** Production spatial sampling + real vanilla materials + independent material/light/bloom fixture. */
public final class ColorLossGpuVerification {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void near(float actual, float expected, String message) {
        check(Float.isFinite(actual) && Math.abs(actual - expected) < .00006f, message + ": " + actual + " != " + expected);
    }
    private static float luma(float[] c) { return c[0]*.299f + c[1]*.587f + c[2]*.114f; }
    private static float[] read(GlTarget target) {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, target.framebuffer);
        float[] value = new float[4]; glReadPixels(0, 0, 1, 1, GL_RGBA, GL_FLOAT, value); return value;
    }
    private static void fill(GlTarget target, float[] rgb) {
        target.bind(); glClearBufferfv(GL_COLOR, 0, new float[]{rgb[0],rgb[1],rgb[2],.37f});
    }
    private static float[] draw(GlProgram p, GlTarget scene, GlTarget target, float[] rgb, float r, float g, float b, int mode) {
        fill(scene, rgb); target.bind(); p.use(); p.texture("Scene",0,scene.textures[0]);
        p.vector("ohColorLossResidual", r,g,b); p.integer("Mode",mode);
        glDrawArrays(GL_TRIANGLES,0,3); return read(target);
    }
    private static void verifySpatialSampling(GlProgram finalPass, GlTarget scene, GlTarget target) {
        // A single texel cannot reveal an unconnected varying, a flipped axis or repeated sampling.
        int width=16, height=8;
        scene.resize(width,height); target.resize(width,height); GlState.fullscreen();
        float[] input=new float[width*height*4];
        for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
            int offset=(y*width+x)*4;
            input[offset]=.1f+.8f*x/(width-1);
            input[offset+1]=.15f+.7f*y/(height-1);
            input[offset+2]=.05f+.9f*((x+3*y)%width)/(width-1);
            input[offset+3]=.1f+.8f*((x+width*y)%17)/16f;
        }
        var pixels=BufferUtils.createFloatBuffer(input.length); pixels.put(input).flip();
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D,scene.textures[0]);
        glTexSubImage2D(GL_TEXTURE_2D,0,0,0,width,height,GL_RGBA,GL_FLOAT,pixels);
        for(float amount:new float[]{0,.35f,1})for(int layered:new int[]{0,1}) {
            target.bind(); finalPass.use(); finalPass.texture("Scene",0,scene.textures[0]);
            finalPass.vector("RgbLoss",amount,amount,amount); finalPass.integer("Layered",layered);
            glDrawArrays(GL_TRIANGLES,0,3);
            float[] output=new float[input.length];
            glReadPixels(0,0,width,height,GL_RGBA,GL_FLOAT,output);
            for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
                int offset=(y*width+x)*4;
                float gray=input[offset]*.2126f+input[offset+1]*.7152f+input[offset+2]*.0722f;
                String sample="spatial sample ("+x+","+y+"), loss="+amount+", layered="+layered;
                for(int c=0;c<3;c++)near(output[offset+c],input[offset+c]+(gray-input[offset+c])*amount,sample+", channel="+c);
                near(output[offset+3],input[offset+3],sample+", alpha");
            }
        }
        System.out.println("Spatial sampling: 16x8 distinct RGBA pixels; identity, partial and full loss; both modes");
    }
    private static double relativeChroma(float[] rgb) {
        double[] c = new double[3];
        for (int i=0;i<3;i++) c[i]=rgb[i]<=.04045 ? rgb[i]/12.92 : Math.pow((rgb[i]+.055)/1.055,2.4);
        double l=Math.cbrt(.4122214708*c[0]+.5363325363*c[1]+.0514459929*c[2]);
        double m=Math.cbrt(.2119034982*c[0]+.6806995451*c[1]+.1073969566*c[2]);
        double t=Math.cbrt(.0883024619*c[0]+.2817188376*c[1]+.6299787005*c[2]);
        double L=.2104542553*l+.793617785*m-.0040720468*t;
        return Math.hypot(1.9779984951*l-2.428592205*m+.4505937099*t,.0259040371*l+.7827717662*m-.808675766*t)/L;
    }
    private static void verifyBlueMaterials(ZipFile vanilla, GlProgram sourcePass, GlProgram finalPass,
                                            GlTarget scene, GlTarget target) throws Exception {
        for(String name:new String[]{"cyan_wool","light_blue_wool","blue_wool","green_wool","dirt","brown_wool"}) {
            var image=ImageIO.read(vanilla.getInputStream(vanilla.getEntry("assets/minecraft/textures/block/"+name+".png")));
            boolean protect=name.equals("green_wool")||name.equals("dirt")||name.equals("brown_wool");
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
                int pixel=image.getRGB(x,y);
                float[] c={(pixel>>16&255)/255f,(pixel>>8&255)/255f,(pixel&255)/255f};
                for(float amount:new float[]{0,.5f,1}) {
                    float loss=protect ? 0 : amount;
                    float[] layered=draw(sourcePass,scene,target,c,0,0,amount,0);
                    for(int channel=0;channel<3;channel++)
                        near(layered[channel],c[channel]+(luma(c)-c[channel])*loss,"Blue source "+name);
                    near(layered[3],.37f,"Blue source alpha");
                    target.bind(); finalPass.use(); finalPass.texture("Scene",0,scene.textures[0]);
                    finalPass.vector("RgbLoss",0,0,amount); finalPass.integer("Layered",0);
                    glDrawArrays(GL_TRIANGLES,0,3);
                    float[] fallback=read(target);
                    float gray=c[0]*.2126f+c[1]*.7152f+c[2]*.0722f;
                    for(int channel=0;channel<3;channel++)
                        near(fallback[channel],c[channel]+(gray-c[channel])*loss,"Blue fallback "+name);
                    near(fallback[3],.37f,"Blue fallback alpha");
                }
            }
            System.out.println("Blue material "+name+": "+(image.getWidth()*image.getHeight())
                    +" texels, source and fallback, identity/half/full, "+(protect?"preserved":"desaturated"));
        }
    }
    private static void verifyRemainingGreen(ZipFile vanilla, GlProgram sourcePass, GlProgram finalPass,
                                             GlTarget scene, GlTarget target) throws Exception {
        for(String name:new String[]{"oak_planks","oak_log","oak_log_top","dirt","brown_wool","purple_wool","green_wool"}) {
            var image=ImageIO.read(vanilla.getInputStream(vanilla.getEntry("assets/minecraft/textures/block/"+name+".png")));
            boolean protect=name.equals("green_wool");
            int tested=0;
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
                int pixel=image.getRGB(x,y);
                float[] c={(pixel>>16&255)/255f,(pixel>>8&255)/255f,(pixel&255)/255f};
                // Near-neutral pixels retain the shared chroma feather and are covered separately.
                if(relativeChroma(c)<.06) continue;
                tested++;
                for(float blue:new float[]{0,.5f,1}) {
                    float loss=protect ? 0 : blue;
                    float[] layered=draw(sourcePass,scene,target,c,1,0,blue,0);
                    for(int channel=0;channel<3;channel++)
                        near(layered[channel],c[channel]+(luma(c)-c[channel])*loss,"remaining Green source "+name);
                    target.bind(); finalPass.use(); finalPass.texture("Scene",0,scene.textures[0]);
                    finalPass.vector("RgbLoss",1,0,blue); finalPass.integer("Layered",0);
                    glDrawArrays(GL_TRIANGLES,0,3);
                    float[] fallback=read(target);
                    float gray=c[0]*.2126f+c[1]*.7152f+c[2]*.0722f;
                    for(int channel=0;channel<3;channel++)
                        near(fallback[channel],c[channel]+(gray-c[channel])*loss,"remaining Green fallback "+name);
                }
            }
            check(tested>0,"remaining Green texture fixture "+name);
            System.out.println("Remaining Green "+name+": "+tested+" colored texels; source/fallback; B=0/.5/1; "+(protect?"preserved":"desaturated"));
        }
    }
    public static void main(String[] args) throws Exception {
        check(glfwInit(), "GLFW"); glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
        glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        long window=glfwCreateWindow(32,32,"RGB loss independent fixture",0,0); check(window!=0,"context");
        glfwMakeContextCurrent(window); GL.createCapabilities(); int vao=glGenVertexArrays(); glBindVertexArray(vao);
        Path shaders=Path.of(args[0]); String common=Files.readString(shaders.resolve("color_loss_common.glsl"));
        String vertex=Files.readString(shaders.resolve("fullscreen.vsh"));
        String fixture="#version 330 core\n#define OH_COLOR_LOSS_PACK\n"+common+"\n"+"""
                uniform sampler2D Scene;
                uniform int Mode;
                in vec2 texCoord;
                out vec4 fragColor;
                void main() {
                    vec4 s = texture(Scene, texCoord);
                    vec3 c = ohLossSrgb(s.rgb);
                    if (Mode == 1) c = ohLitMaterial(s.rgb, vec3(2.0, 0.04, 0.02), vec3(0.0));
                    if (Mode == 2) c = mix(ohLitMaterial(s.rgb, vec3(2.0, 0.04, 0.02), vec3(0.0)), ohLossSrgb(vec3(3.0, 0.02, 0.01)), 0.12);
                    if (Mode == 3) c = ohLossLinear(s.rgb);
                    if (Mode == 4) c = vec3(ohHueLoss(s.r, ohColorLossResidual));
                    // SSR is an already corrected input: mixing it must not reclassify it.
                    if (Mode == 5) c = mix(s.rgb, ohLossSrgb(vec3(0.8, 0.01, 0.01)), 0.25);
                    fragColor = vec4(c, s.a);
                }
                """;
        try (var p=new GlProgram(vertex,fixture);
             var finalPass=new GlProgram(vertex,Files.readString(shaders.resolve("color_loss.fsh")).replace("#include \"color_loss_common.glsl\"",common));
             var scene=new GlTarget(GL_RGBA32F); var target=new GlTarget(GL_RGBA32F)) {
            verifySpatialSampling(finalPass,scene,target);
            scene.resize(1,1); target.resize(1,1); GlState.fullscreen();
            try (var vanilla=new ZipFile(args[1])) {
                for(String name:new String[]{"dirt","coarse_dirt","brown_wool","red_wool","red_concrete","red_terracotta","bricks"}) {
                    var image=ImageIO.read(vanilla.getInputStream(vanilla.getEntry("assets/minecraft/textures/block/"+name+".png")));
                    int colored=0, correct=0;
                    boolean protect=name.equals("dirt")||name.equals("coarse_dirt")||name.equals("brown_wool");
                    for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
                        int pixel=image.getRGB(x,y);
                        float[] c={(pixel>>16&255)/255f,(pixel>>8&255)/255f,(pixel&255)/255f};
                        // Muted mortar uses the specified relative-chroma feather, not full selective loss.
                        if(relativeChroma(c)<.06) continue;
                        colored++; float[] result=draw(p,scene,target,c,1,0,0,0);
                        boolean passed=protect ? Math.abs(result[0]-c[0])+Math.abs(result[1]-c[1])+Math.abs(result[2]-c[2])<.001
                                : Math.abs(result[0]-result[1])+Math.abs(result[1]-result[2])<.001;
                        if(passed) correct++;
                        near(result[3],.37f,"material alpha"); near(luma(result),luma(c),"material luminance");
                    }
                    check(correct>=colored*.94,"material "+name+" correct="+correct+"/"+colored);
                    System.out.println("Material "+name+": "+correct+"/"+colored);
                }
                verifyBlueMaterials(vanilla,p,finalPass,scene,target);
                verifyRemainingGreen(vanilla,p,finalPass,scene,target);
            }
            // Dark red remains classified even under dim illumination.
            for(float scale:new float[]{1,.1f,.01f}) {
                float[] out=draw(p,scene,target,new float[]{.7f*scale,.02f*scale,.01f*scale},1,0,0,0);
                near(out[0],out[1],"dark red"); near(out[1],out[2],"dark red");
            }
            float[] brown={.46f,.32f,.20f};
            float[] lit=draw(p,scene,target,brown,1,0,0,1);
            near(lit[0]/brown[0],lit[1]/brown[1],"brown albedo under neutralized red light");
            near(lit[1]/brown[1],lit[2]/brown[2],"brown albedo blue ratio");
            near(luma(lit),luma(new float[]{brown[0]*2,brown[1]*.04f,brown[2]*.02f}),"lighting brightness");
            float[] bloom=draw(p,scene,target,brown,1,0,0,2);
            float neutralBloom=luma(new float[]{3,.02f,.01f});
            for(int i=0;i<3;i++) near(bloom[i],lit[i]*.88f+neutralBloom*.12f,"neutral bloom over brown");
            check(bloom[0]>bloom[1] && bloom[1]>bloom[2],"bloom background still brown");
            float[] linear=draw(p,scene,target,new float[]{8,.01f,.002f},1,0,0,3);
            near(linear[0],linear[1],"linear red light"); near(linear[1],linear[2],"linear red light");
            // Cyan and blue share B; check the remaining mixed colors and continuous boundaries.
            float[] hues={20,75,140,200,270,320};
            for(float r:new float[]{0,.5f,1})for(float g:new float[]{0,.5f,1})for(float b:new float[]{0,.5f,1}) {
                float[] sorted={r,g,b}; java.util.Arrays.sort(sorted);
                float[] expected={r,sorted[1],g,b,b,sorted[1]};
                for(int i=0;i<hues.length;i++) near(draw(p,scene,target,new float[]{hues[i],0,0},r,g,b,4)[0],expected[i],"mixed color coupling");
            }
            for(float h:new float[]{0,40,52,104,118,168,190,208,224,288,302,338,350,360}) {
                float before=draw(p,scene,target,new float[]{(h+359.999f)%360,0,0},1,.3f,.7f,4)[0];
                float after=draw(p,scene,target,new float[]{(h+.001f)%360,0,0},1,.3f,.7f,4)[0];
                near(before,after,"continuous hue boundary");
            }
            float[] gray={.0001f,.0001f,.0001f};
            float[] unchanged=draw(p,scene,target,gray,1,0,0,0);
            near(unchanged[0],gray[0],"near-neutral stability");
            // Final pass: exact identity, equal RGB == global fade, full gray, unsupported approximation.
            for(float amount:new float[]{0,.35f,1}) for(int layered:new int[]{0,1}) {
                fill(scene,brown); glEnable(GL_BLEND); glEnable(GL_SCISSOR_TEST); glScissor(0,0,0,0); glActiveTexture(GL_TEXTURE5);
                int fbo=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), oldProgram=glGetInteger(GL_CURRENT_PROGRAM);
                try(var saved=GlState.capture()) {
                    GlState.fullscreen(); target.bind(); finalPass.use(); finalPass.texture("Scene",0,scene.textures[0]);
                    finalPass.vector("RgbLoss",amount,amount,amount); finalPass.integer("Layered",layered); glDrawArrays(GL_TRIANGLES,0,3);
                }
                check(glIsEnabled(GL_BLEND)&&glIsEnabled(GL_SCISSOR_TEST)&&glGetInteger(GL_ACTIVE_TEXTURE)==GL_TEXTURE5
                        && glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)==fbo && glGetInteger(GL_CURRENT_PROGRAM)==oldProgram,"host GL restoration");
                float[] out=read(target); float y=brown[0]*.2126f+brown[1]*.7152f+brown[2]*.0722f;
                for(int c=0;c<3;c++)near(out[c],brown[c]+(y-brown[c])*amount,"uniform all equivalence");
                near(out[3],.37f,"final alpha"); GlState.fullscreen();
            }
            check(glGetError()==GL_NO_ERROR,"GL errors");
            System.out.println("PASS: "+glGetString(GL_RENDERER)+"; actual material textures, RGB linkage, brown + red light + bloom, linear sources, full gray, alpha and GL state");
        } finally { glDeleteVertexArrays(vao); glfwDestroyWindow(window); glfwTerminate(); }
    }
}
