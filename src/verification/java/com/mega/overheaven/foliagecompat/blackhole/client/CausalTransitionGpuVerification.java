package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static com.mega.overheaven.foliagecompat.blackhole.client.FinaleGpuVerification.*;
import com.mega.overheaven.foliagecompat.finale.*;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Font;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;

/** Production intro shaders and resource owner against a synthetic current-frame world. */
public final class CausalTransitionGpuVerification {
    static final FinaleTimeline TIMELINE=FinaleTimeline.defaults();
    static final StringBuilder REPORT=new StringBuilder();
    static GlCausalBackground opening;
    static boolean openingEnabled=true;
    static void expect(boolean ok,String name){check(ok,name);REPORT.append("PASS ").append(name).append('\n');}
    static void finite(float[] pixels,String name){for(float v:pixels)if(!Float.isFinite(v))throw new AssertionError(name);expect(true,name);}
    static double diff(float[] a,float[] b){double v=0;for(int i=0;i<a.length;i+=4)for(int c=0;c<3;c++)v+=Math.abs(a[i+c]-b[i+c]);return v/(a.length*.75);}
    static void fixture(FinaleFlashGpuVerification.Pipeline p,GlProgram fill,Matrix4f view,int mode,boolean night){
        GlState.fullscreen();fill.use();p.material(fill,0,view,0,-32,true,H);fill.integer("Mode",mode);fill.integer("Night",night?1:0);
        p.opaque.bind();fill.integer("Layer",0);draw();p.surface.bind();fill.integer("Layer",1);draw();p.world.bind();fill.integer("Layer",2);draw();
    }
    static void render(FinaleFlashGpuVerification.Pipeline p,GlProgram fill,double seconds,Matrix4f view,int mode,boolean night){
        fixture(p,fill,view,mode,night);double ticks=seconds*20;
        var state=new CausalTransition.State();CausalTransition.evaluate(TIMELINE,ticks,-1,state);
        if(openingEnabled&&state.backgroundGain<1) {
            background(p,ticks,view);p.blit(opening.result,p.cosmos,GL_NEAREST);p.sceneOpaqueOverride=opening.result.textures[1];
        } else {p.blit(p.world,p.cosmos,GL_NEAREST);p.sceneOpaqueOverride=p.opaque.textures[0];}
        p.subject(ticks,view,0,-32,true);p.blit(p.cosmos,p.world,GL_NEAREST);p.compose(ticks,-1);
    }
    static void background(FinaleFlashGpuVerification.Pipeline p,double ticks,Matrix4f view) {
        opening.render(p.world.textures[0],p.opaque.textures[0],W,H,vao,
                program->p.material(program,ticks,view,0,-32,true,H));
    }
    static double scaledError(float[] a,float[] b,double scale) {double e=0;for(int i=0;i<a.length;i+=4)for(int c=0;c<3;c++)e=Math.max(e,Math.abs(a[i+c]*scale-b[i+c]));return e;}
    static double measure(Runnable action){for(int n=0;n<12;n++)action.run();double[] times=new double[3];for(int r=0;r<3;r++){int q=glGenQueries();glBeginQuery(GL_TIME_ELAPSED,q);for(int n=0;n<24;n++)action.run();glEndQuery(GL_TIME_ELAPSED);times[r]=glGetQueryObjectui64(q,GL_QUERY_RESULT)/24e6;glDeleteQueries(q);}Arrays.sort(times);return times[1];}
    static void frame(GlTarget target,Path file,boolean preview)throws Exception{
        float[] p=pixels(target);int w=preview?960:target.width,h=preview?540:target.height;var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int at=((y*target.height/h)*target.width+x*target.width/w)*4,rgb=0;for(int c=0;c<3;c++)rgb|=Math.max(0,Math.min(255,Math.round(p[at+c]*255)))<<(16-8*c);image.setRGB(x,h-1-y,rgb);}ImageIO.write(image,preview?"jpg":"png",file.toFile());
    }
    static void impactToneChecks(FinaleFlashGpuVerification.Pipeline p) {
        float[][] swatches={{.2f,.2f,.2f,1},{.55f,.55f,.55f,1},{.45f,.45f,.45f,1},{1,0,0,1},{0,1,0,1},{0,0,1,1},{2,3,4,1}};
        for(int i=0;i<swatches.length;i++) {
            p.world.bind();glClearBufferfv(GL_COLOR,0,swatches[i]);p.compose(4.475*20,-1);float[] result=pixels(p.result);
            double value=result[(H/2*W+W/2)*4];
            if(i==0||i==3)expect(value==0,"flat shadow/red patch is filled black index="+i);
            else if(i==2)expect(value>.2&&value<.8,"narrow midtone band survives instead of binary edge detection");
            else expect(value==1,"flat bright/green/blue/HDR patch is filled white index="+i);
            double variation=0;for(int n=0;n<result.length;n+=4)for(int c=0;c<3;c++)variation=Math.max(variation,Math.abs(result[n+c]-value));
            expect(variation<.0001,"uniform surface stays a filled achromatic region index="+i);
        }
    }
    static void impactChecks(FinaleFlashGpuVerification.Pipeline p,GlProgram fill,Matrix4f view)throws Exception {
        impactToneChecks(p);
        for(double time:new double[]{4.39,4.415,4.475,4.515,4.565,4.60}) {
            p.impactEnabled=false;render(p,fill,time,view,0,false);float[] before=pixels(p.result),radiation=pixels(p.solution);
            frame(p.result,output.resolve(String.format(Locale.ROOT,"impact-off-%06.3f.png",time)),false);
            p.impactEnabled=true;render(p,fill,time,view,0,false);float[] after=pixels(p.result);
            frame(p.result,output.resolve(String.format(Locale.ROOT,"impact-on-%06.3f.png",time)),false);
            expect(Arrays.equals(radiation,pixels(p.solution)),"impact does not resample or duplicate subject t="+time);
            if(time<4.4||time>=4.60)expect(diff(before,after)<.0001,"impact clears exactly outside the authored window t="+time);
            else {
                expect(diff(before,after)>.025,"impact is visible in final color t="+time);
                if(time<4.535) {
                    double chroma=0,min=1,max=0;
                    for(int i=0;i<after.length;i+=4){chroma=Math.max(chroma,Math.max(Math.abs(after[i]-after[i+1]),Math.abs(after[i]-after[i+2])));min=Math.min(min,after[i]);max=Math.max(max,after[i]);}
                    expect(chroma<.00001&&min<.01&&max>.99,"true black and white with visible silhouettes t="+time);
                }
            }
        }
        render(p,fill,4.475,view,0,false);float[] positive=pixels(p.result);
        p.result.bind();p.late.use();p.late.scalar("IntroImpactPolarity",1);draw();float[] negative=pixels(p.result);
        double inverseError=0;for(int i=0;i<positive.length;i+=4)for(int c=0;c<3;c++)inverseError=Math.max(inverseError,Math.abs(positive[i+c]+negative[i+c]-1));
        expect(inverseError<.001,"white and black beats invert the same visible contours");
        p.result.bind();p.late.use();p.late.scalar("Flash",1);p.late.integer("Black",0);draw();float[] white=pixels(p.result);
        boolean exact=true;for(int i=0;i<white.length;i+=4)for(int c=0;c<3;c++)exact&=white[i+c]==1;
        expect(exact,"later full-white exposure takes priority over impact frames");
        p.result.bind();p.late.use();p.late.integer("Black",1);draw();float[] black=pixels(p.result);
        exact=true;for(int i=0;i<black.length;i+=4)for(int c=0;c<3;c++)exact&=black[i+c]==0;
        expect(exact,"final pure black takes priority over impact frames");
        for(Matrix4f camera:new Matrix4f[]{new Matrix4f().rotationY((float)Math.PI),new Matrix4f().rotationX(1.5f)}) {
            render(p,fill,4.475,camera,0,false);float[] image=pixels(p.result);double chroma=0;
            for(int i=0;i<image.length;i+=4)chroma=Math.max(chroma,Math.abs(image[i]-image[i+2]));
            expect(chroma<.00001,"impact covers the view even with subject offscreen");
        }
        render(p,fill,4.475,view,0,false);float[] frozen=pixels(p.result);render(p,fill,4.475,view,0,false);
        expect(Arrays.equals(frozen,pixels(p.result)),"paused impact frames are deterministic");
        p.compose(4.475*20,1);float[] recovered=pixels(p.result);p.impactEnabled=false;p.compose(4.475*20,1);
        expect(diff(recovered,pixels(p.result))<.0001,"stop recovery clears impact without replay");p.impactEnabled=true;
        render(p,fill,4.475,view,0,false);p.impactEnabled=false;double off=measure(()->p.compose(4.475*20,-1));
        p.impactEnabled=true;double on=measure(()->p.compose(4.475*20,-1));
        REPORT.append(String.format(Locale.ROOT,"isolated late composite: impact off/on %.3f / %.3f ms, delta %+.3f ms%n",off,on,on-off));
        var board=new BufferedImage(1440,1290,BufferedImage.TYPE_INT_RGB);var g=board.createGraphics();g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,20));
        for(int row=0;row<3;row++)for(int col=0;col<2;col++) {
            double time=new double[]{4.415,4.475,4.515}[row];String mode=col==0?"off":"on";
            var image=ImageIO.read(output.resolve(String.format(Locale.ROOT,"impact-%s-%06.3f.png",mode,time)).toFile());
            g.drawImage(image,col*720,row*430,720,405,null);g.setColor(Color.WHITE);
            g.drawString(String.format(Locale.ROOT,"%.3fs - impact %s",time,mode),col*720+12,row*430+426);
        }
        g.dispose();ImageIO.write(board,"png",output.resolve("impact-comparison.png").toFile());
    }
    public static void main(String[] args)throws Exception{
        source=Path.of(args[0]);output=Path.of(args[1]);Files.createDirectories(output);Files.createDirectories(output.resolve("frames"));
        expect(glfwInit(),"GLFW initialized");glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
        long window=glfwCreateWindow(64,64,"Causal transition verification",0,0);expect(window!=0,"hidden context");glfwMakeContextCurrent(window);GL.createCapabilities();vao=glGenVertexArrays();
        REPORT.append("GPU: ").append(glGetString(GL_RENDERER)).append("; 1920x1080 synthetic scene; production GLSL. No Minecraft, Oculus or EndingLibrary execution.\n");
        var view=new Matrix4f();
        opening=new GlCausalBackground(read("fullscreen.vsh"),read("causal_background.fsh"));
        try(var p=new FinaleFlashGpuVerification.Pipeline();var fill=new GlProgram(read("fullscreen.vsh"),Files.readString(Path.of("src/verification/resources/causal_fixture.fsh")))){
            p.resize(48,.5f);GlState.fullscreen();
            double[] times={0,2,4,4.06,4.2,4.4,4.55,6.6,8};
            try(var probe=new GlProgram(read("fullscreen.vsh"),"#version 330 core\nin vec2 texCoord;out vec4 Color;\n"+read("field.glsl")+"\nvoid main(){vec2 hit=sphere(Eye-Center,rayAt(texCoord),max(coreRadius(),.000001));Color=vec4(hit.y>0.?1.:0.,coreRadius(),finaleRadius(),1.);}");var mask=new GlTarget(GL_RGBA32F)) {
                mask.resize(W,H);int initialArea=0;double initialSingularity=0;
                for(double second:new double[]{0,4,4.2,4.4}) {
                    mask.bind();probe.use();p.material(probe,second*20,view,0,-32,true,H);draw();float[] pixels=pixels(mask);int area=0;for(int n=0;n<pixels.length;n+=4)if(pixels[n]>.5)area++;
                    if(second==0){initialArea=area;initialSingularity=pixels[2];}
                    if(second==4)expect(area>initialArea*1.54&&area<initialArea*1.60,"full resolution horizon grows about 25 percent in radius");
                    if(second==4.4)expect(area<initialArea*.37&&area>initialArea*.34,"full resolution horizon contracts to 60 percent before reveal");
                    expect(Math.abs(pixels[2]-initialSingularity)<1e-6,"base singularity size remains unchanged t="+second);
                }
            }
            for(int mode:new int[]{0,3,6}) {
                fixture(p,fill,view,mode,false);float[] original=pixels(p.world);background(p,88,view);
                expect(scaledError(original,pixels(opening.result),.25)<.001,"exact one-quarter dim including transparent residual mode="+mode);
            }
            fixture(p,fill,view,1,false);float[] nearScene=pixels(p.world);background(p,89.2,view);
            var envelope=new CausalTransition.State();CausalTransition.evaluate(TIMELINE,89.2,-1,envelope);
            expect(scaledError(nearScene,pixels(opening.result),envelope.backgroundGain)<.001,"near 1.5-block wall receives gain but zero displacement");
            fixture(p,fill,view,0,false);float[] originalScene=pixels(p.world);background(p,89.2,view);float[] displaced=pixels(opening.result);
            expect(scaledError(originalScene,displaced,envelope.backgroundGain)<.001,"background has no radial movement at release");
            double cornerError=0;for(int y:new int[]{0,H-1})for(int x:new int[]{0,W-1})for(int c=0;c<3;c++) {int i=(y*W+x)*4+c;cornerError=Math.max(cornerError,Math.abs(displaced[i]-originalScene[i]*envelope.backgroundGain));}
            expect(cornerError<.001,"corner footprint contracts to identity without edge stretching");
            for(Matrix4f back:new Matrix4f[]{new Matrix4f().rotationY((float)Math.PI),new Matrix4f().rotationY(1.1f)}) {
                fixture(p,fill,back,0,false);float[] a=pixels(p.world);background(p,89.2,back);
                expect(scaledError(a,pixels(opening.result),envelope.backgroundGain)<.001,"behind/offscreen center skips trail while dimming continues");
            }
            openingEnabled=false;render(p,fill,3.5,view,0,false);float[] radiation=pixels(p.solution);openingEnabled=true;render(p,fill,3.5,view,0,false);
            expect(Arrays.equals(radiation,pixels(p.solution)),"background dim leaves subject radiation bit-identical");
            openingEnabled=false;render(p,fill,4.46,view,0,false);radiation=pixels(p.solution);openingEnabled=true;render(p,fill,4.46,view,0,false);
            expect(Arrays.equals(radiation,pixels(p.solution)),"background color treatment leaves singularity and membrane radiation bit-identical");
            for(double offset:new double[]{0,.015,.055,.085,.115,.16,.20}) {render(p,fill,4.4+offset,view,0,false);frame(p.result,output.resolve(String.format(Locale.ROOT,"release-%04.2f.png",offset)),false);}

            impactChecks(p,fill,view);

            double[] handoff={4,4.1,4.2,4.3,4.4,4.43,4.46,4.5,4.55};
            for(double time:handoff) {
                render(p,fill,time,view,0,false);finite(pixels(p.result),"contraction/reveal handoff finite t="+time);
                frame(p.result,output.resolve(String.format(Locale.ROOT,"handoff-%05.2f.png",time)),false);
            }
            var handoffBoard=new BufferedImage(1440,900,BufferedImage.TYPE_INT_RGB);var handoffGraphics=handoffBoard.createGraphics();
            handoffGraphics.setFont(new Font(Font.SANS_SERIF,Font.BOLD,18));
            for(int i=0;i<handoff.length;i++) {
                var image=ImageIO.read(output.resolve(String.format(Locale.ROOT,"handoff-%05.2f.png",handoff[i])).toFile());
                int x=i%3*480,y=i/3*300;handoffGraphics.drawImage(image,x,y,480,270,null);handoffGraphics.setColor(Color.WHITE);
                handoffGraphics.drawString(handoff[i]+" s - continuous contraction / reveal",x+12,y+292);
            }
            handoffGraphics.dispose();ImageIO.write(handoffBoard,"png",output.resolve("handoff-storyboard.png").toFile());

            for(double time:times){render(p,fill,time,view,0,false);finite(pixels(p.result),"fixed frame finite t="+time);frame(p.result,output.resolve(String.format(Locale.ROOT,"frame-%05.2f.png",time)),false);}
            for(Matrix4f camera:new Matrix4f[]{view,new Matrix4f().rotationY((float)Math.PI),new Matrix4f().rotationX(1.5f),new Matrix4f().rotationY(1.1f),new Matrix4f().translate(0,0,31.8f)}){
                render(p,fill,3.5,camera,0,false);finite(pixels(p.result),"finite forward/back/up/offscreen/inside subject");
            }
            render(p,fill,4.65,view,1,false);
            float[] blockedLight=pixels(p.solution);double lightPeak=0;for(int i=0;i<blockedLight.length;i+=4)for(int c=0;c<3;c++)lightPeak=Math.max(lightPeak,blockedLight[i+c]);
            expect(lightPeak==0,"full wall has exactly zero direct front radiation");
            for(int mode:new int[]{0,2,3,4,5,6})for(boolean night:new boolean[]{false,true}){render(p,fill,4.55,view,mode,night);finite(pixels(p.result),"wall/edge/glass/entity/cutout/water fixture mode="+mode+" night="+night);}
            render(p,fill,3.5,view,0,false);float[] paused=pixels(p.result);render(p,fill,3.5,view,0,false);expect(Arrays.equals(paused,pixels(p.result)),"paused subject deterministic without image history");
            for(int steps:new int[]{24,48,96}){p.resize(steps,steps==24?.375f:steps==96?.75f:.5f);render(p,fill,3.5,view,0,false);finite(pixels(p.result),"quality "+steps);}
            p.resize(48,.5f);
            for(float fov:new float[]{40,70,110})for(float aspect:new float[]{1,16f/9,21f/9}){p.projection.identity().perspective((float)Math.toRadians(fov),aspect,.05f,4096);render(p,fill,4.55,view,0,false);finite(pixels(p.result),"FOV/aspect "+fov+"/"+aspect);}
            p.projection.identity().perspective((float)Math.toRadians(70),W/(float)H,.05f,4096);
            for(int mode:new int[]{1,2}){p.layerMode=mode;p.displaySpace=0;render(p,fill,4.55,view,3,false);finite(pixels(p.cosmos),"HDR layered subject mode="+mode);}p.layerMode=0;p.displaySpace=1;
            for(double time:new double[]{1.4,4.46,5.3}) {
                for(int warm=0;warm<240;warm++) {openingEnabled=warm%2==0;render(p,fill,time,view,0,false);}openingEnabled=true;
                openingEnabled=false;double baseline=measure(()->render(p,fill,time,view,0,false));openingEnabled=true;
                double updated=measure(()->render(p,fill,time,view,0,false));
                REPORT.append(String.format(Locale.ROOT,"t=%.2fs background off/on %.3f / %.3f ms, delta %+.3f ms\n",time,baseline,updated,updated-baseline));
                if(time<4.62) {fixture(p,fill,view,0,false);REPORT.append(String.format(Locale.ROOT,"t=%.2fs isolated environment MRT %.3f ms\n",time,measure(()->background(p,time*20,view))));}
            }
            fixture(p,fill,view,0,false);p.world.bind();glViewport(7,9,101,103);glEnable(GL_BLEND);glEnable(GL_DEPTH_TEST);glDepthMask(true);fill.use();
            int savedProgram=glGetInteger(GL_CURRENT_PROGRAM),savedFbo=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
            try(var scope=GlState.capture()){GlState.fullscreen();render(p,fill,3.5,view,0,false);}
            int[] restored=new int[4];glGetIntegerv(GL_VIEWPORT,restored);
            expect(glGetInteger(GL_CURRENT_PROGRAM)==savedProgram&&glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)==savedFbo&&Arrays.equals(restored,new int[]{7,9,101,103})&&glIsEnabled(GL_BLEND)&&glIsEnabled(GL_DEPTH_TEST)&&glGetBoolean(GL_DEPTH_WRITEMASK),"GL scope restores program, framebuffer, viewport, blend and depth");GlState.fullscreen();
            fixture(p,fill,view,0,false);background(p,70,view);int previousFbo=opening.result.framebuffer,previousTexture=opening.result.textures[0];background(p,70,view);
            expect(previousFbo==opening.result.framebuffer&&previousTexture==opening.result.textures[0],"environment targets reused at fixed resolution");
            opening.result.resize(800,600);expect(opening.result.width==800&&opening.result.height==600,"environment target follows resize");background(p,70,view);
            expect(opening.result.width==W&&opening.result.height==H,"environment target returns to full resolution");
            previousFbo=opening.result.framebuffer;previousTexture=opening.result.textures[0];opening.close();
            expect(!glIsFramebuffer(previousFbo)&&!glIsTexture(previousTexture),"environment resources deleted on close");
            opening=new GlCausalBackground(read("fullscreen.vsh"),read("causal_background.fsh"));background(p,70,view);expect(glIsFramebuffer(opening.result.framebuffer),"reload creates fresh target without frame history");
            p.world.bind();glClearBufferfv(GL_COLOR,0,new float[]{8,3,.5f,.65f});background(p,88,view);float[] hdr=pixels(opening.result);
            expect(Math.abs(hdr[0]-2)<.001&&Math.abs(hdr[1]-.75)<.001&&Math.abs(hdr[3]-.65)<.001,"HDR gain retains values above one and preserves alpha");
            var cameraFeedback=new CausalTransition.Feedback();
            for(int i=0;i<192;i++){cameraFeedback.update(TIMELINE,i*20/24.0,false);
                var previewView=new Matrix4f().rotateZ((float)Math.toRadians(cameraFeedback.roll)).rotateX((float)Math.toRadians(cameraFeedback.pitch)).rotateY((float)Math.toRadians(cameraFeedback.yaw));
                render(p,fill,i/24.0,previewView,0,false);frame(p.result,output.resolve(String.format(Locale.ROOT,"frames/%04d.jpg",i)),true);}
            var board=new BufferedImage(1440,900,BufferedImage.TYPE_INT_RGB);var g=board.createGraphics();g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,18));for(int i=0;i<times.length;i++){var img=ImageIO.read(output.resolve(String.format(Locale.ROOT,"frame-%05.2f.png",times[i])).toFile());int x=i%3*480,y=i/3*300;g.drawImage(img,x,y,480,270,null);g.setColor(Color.WHITE);g.drawString(times[i]+" s - reveal choreography",x+12,y+292);}g.dispose();ImageIO.write(board,"png",output.resolve("storyboard.png").toFile());
            expect(glGetError()==GL_NO_ERROR,"OpenGL error free");
        }finally{opening.close();glDeleteVertexArrays(vao);glfwDestroyWindow(window);glfwTerminate();}
        REPORT.append("Static GPU fixtures do not certify live host transparency, shader-pack ordering or sound playback.\n");Files.writeString(output.resolve("report.txt"),REPORT);System.out.println(REPORT);
    }
}
