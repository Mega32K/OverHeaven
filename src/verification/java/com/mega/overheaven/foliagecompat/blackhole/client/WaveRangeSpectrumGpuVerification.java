package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static com.mega.overheaven.foliagecompat.blackhole.client.FinaleGpuVerification.*;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Font;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL;

/** Actual production shaders against synthetic scenery; never launches Minecraft. */
public final class WaveRangeSpectrumGpuVerification {
    private static final StringBuilder REPORT=new StringBuilder();
    private static final Matrix4f VIEW=new Matrix4f();
    private static final double TICKS=836;
    private static final String FIXTURE="""
        #version 330 core
        in vec2 texCoord; out vec4 Color; uniform float SceneGain;
        void main(){
            vec2 p=texCoord;
            float checker=step(.5,fract(p.x*40.))*step(.5,fract(p.y*22.5));
            float horizon=smoothstep(.40,.43,p.y);
            vec3 c=mix(vec3(.08,.11,.15),vec3(.28,.42,.57),p.y);
            c=mix(c,c*.35,checker*(1.-horizon)*.7);
            float buildings=step(p.y,.40+.15*fract(sin(floor(p.x*22.)*17.3)*31.7));
            c=mix(c,vec3(.21,.24,.28)+checker*.13,buildings);
            float lines=step(.94,fract(p.x*40.))*step(p.y,.52);
            c+=lines*.24;
            Color=vec4(c*SceneGain,1.);
        }
        """;
    private static void expect(boolean ok,String text){check(ok,text);REPORT.append("PASS ").append(text).append('\n');}
    private static double difference(float[] a,float[] b){double d=0;for(int i=0;i<a.length;i+=4)for(int c=0;c<3;c++)d+=Math.abs(a[i+c]-b[i+c]);return d/(a.length*.75);}
    private static void finite(float[] a,String text){for(float f:a)if(!Float.isFinite(f))throw new AssertionError(text);expect(true,text);}
    private static float depth(FinaleFlashGpuVerification.Pipeline p,float distance){var c=p.projection.transform(new Vector4f(0,0,-distance,1));return c.z/c.w*.5f+.5f;}
    private static void prepare(FinaleFlashGpuVerification.Pipeline p,GlProgram fill,int wall,boolean glass){
        GlState.fullscreen();p.cosmos.bind();fill.use();draw();p.depth(wall==1?depth(p,8):1);
        if(wall==2){p.opaque.bind();glEnable(GL_SCISSOR_TEST);glScissor(0,0,W/2,H);glClearBufferfv(GL_COLOR,1,new float[]{depth(p,8),0,0,0});glDisable(GL_SCISSOR_TEST);}
        if(glass){p.surface.bind();glClearBufferfv(GL_COLOR,0,new float[]{.6f,.8f,.9f,1});glClearBufferfv(GL_COLOR,1,new float[]{depth(p,8),0,0,0});}
    }
    private static float[] render(FinaleFlashGpuVerification.Pipeline p,GlProgram fill,boolean enabled,int wall,boolean glass){
        prepare(p,fill,wall,glass);p.spectrumEnabled=enabled;p.subject(TICKS,VIEW,0,-32,true);return pixels(p.cosmos);
    }
    private static float[] layer(FinaleFlashGpuVerification.Pipeline p,GlProgram fill,double tick,boolean enabled,int display,int mode) {
        prepare(p,fill,0,false);p.spectrumEnabled=enabled;p.subject(tick,VIEW,0,-32,true);
        p.result.bind();p.composite.use();p.composite.integer("DisplaySpace",display);p.composite.integer("LayerMode",mode);draw();
        return pixels(p.result);
    }
    private static void allWaves(FinaleFlashGpuVerification.Pipeline p,FinaleFlashGpuVerification.Pipeline old,GlProgram fill)throws Exception {
        var timeline=com.mega.overheaven.foliagecompat.finale.FinaleTimeline.defaults();
        for(int id=0;id<7;id++) {
            double tick=com.mega.overheaven.foliagecompat.finale.FinaleWaves.start(timeline,id)
                +com.mega.overheaven.foliagecompat.finale.FinaleWaves.duration(timeline,id)*.65;
            for(float gain:new float[]{1,.08f}) for(int display:new int[]{1,0}) {
                int mode=display==1?0:1;fill.use();fill.scalar("SceneGain",gain);
                float[] neutral=layer(p,fill,tick,false,display,mode),spectrum=layer(p,fill,tick,true,display,mode);
                double delta=difference(neutral,spectrum);
                expect(delta>.00001,"wave "+id+" disperses scenery, gain="+gain+" display="+display+" layer="+mode+" delta="+delta);
                {
                    float[] previous=layer(old,fill,tick,true,display,mode);
                    double previousDelta=difference(neutral,previous);
                    expect(delta>previousDelta*1.15,"broader and stronger than previous tuning for wave "+id+" gain="+gain+" display="+display+" before="+previousDelta+" after="+delta);
                }
                if(id==0 && gain==.08f && display==0) {
                    layer(old,fill,tick,true,display,mode);image(old.result,"ordinary-night-before");
                    layer(p,fill,tick,true,display,mode);image(p.result,"ordinary-night-after");
                }
                if(id==0 && gain==1 && display==1) {
                    layer(old,fill,tick,true,display,mode);image(old.result,"ordinary-before");
                    layer(p,fill,tick,true,display,mode);image(p.result,"ordinary-after");
                }
            }
            fill.use();fill.scalar("SceneGain",1);
            float[] front=layer(p,fill,tick,false,0,2),coloredFront=layer(p,fill,tick,true,0,2);
            expect(Arrays.equals(front,coloredFront),"wave "+id+" spectrum never tints the Oculus foreground radiation layer");
        }
        for(double tick:new double[]{0,160,500,1000,2288}) {
            float[] off=layer(p,fill,tick,false,1,0),on=layer(p,fill,tick,true,1,0);
            expect(Arrays.equals(off,on),"no local spectrum without an active wave, tick="+tick);
        }
        var board=new BufferedImage(1920,660,BufferedImage.TYPE_INT_RGB);var g=board.createGraphics();g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,22));
        String[] pictures={"ordinary-before","ordinary-after"};for(int n=0;n<2;n++){g.drawImage(ImageIO.read(output.resolve(pictures[n]+".png").toFile()),n*960,0,960,540,null);g.setColor(Color.WHITE);g.drawString(n==0?"BEFORE":"AFTER - WIDER, STRONGER SCENERY DISPERSION",n*960+20,580);}
        g.drawString("First wave; identical time and camera. Synthetic scene, not a Minecraft screenshot.",20,630);g.dispose();ImageIO.write(board,"png",output.resolve("ordinary-before-after.png").toFile());
    }
    private static GlProgram probe(Path resources)throws Exception {
        String frag="#version 330 core\nin vec2 texCoord;out vec4 Color;\n"+VerificationShaders.read(resources,"field.glsl")+"""
            void main(){
                vec3 direction=rayAt(texCoord),origin=Eye-Center;
                vec4 s=energySpectrum(direction,distanceAt(texCoord,texture(OpaqueDepth,texCoord).r));
                float impact=length(origin+direction*max(0.,-dot(origin,direction)));
                Color=vec4(length(s.xy*vec2(textureSize(OpaqueDepth,0))),s.z,impact>WaveRadius?1.:0.,s.w);
            }
            """;
        return new GlProgram(VerificationShaders.read(resources,"fullscreen.vsh"),frag);
    }
    private static float[] sample(FinaleFlashGpuVerification.Pipeline p,GlProgram probe,GlTarget target,double tick,Matrix4f view){
        target.bind();GlState.fullscreen();probe.use();p.material(probe,tick,view,0,-32,true,H);draw();return pixels(target);
    }
    private static double measure(Runnable draw){
        double[] times=new double[3];
        for(int pass=0;pass<3;pass++){for(int n=0;n<8;n++)draw.run();int q=glGenQueries();glBeginQuery(GL_TIME_ELAPSED,q);for(int n=0;n<32;n++)draw.run();glEndQuery(GL_TIME_ELAPSED);times[pass]=glGetQueryObjectui64(q,GL_QUERY_RESULT)/32e6;glDeleteQueries(q);}
        Arrays.sort(times);return times[1];
    }
    public static void main(String[] args)throws Exception {
        source=Path.of(args[0]);output=Path.of(args[1]);Path before=Path.of(args[2]);Files.createDirectories(output);
        expect(glfwInit(),"GLFW initialized");glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
        long window=glfwCreateWindow(64,64,"wave scenery spectrum",0,0);expect(window!=0,"hidden context");glfwMakeContextCurrent(window);GL.createCapabilities();vao=glGenVertexArrays();
        REPORT.append("GPU ").append(glGetString(GL_RENDERER)).append("; 1920x1080 balanced; fixed seed/camera/time; production shaders, synthetic scene, no host/game execution.\n");
        try(var p=new FinaleFlashGpuVerification.Pipeline();var old=new FinaleFlashGpuVerification.Pipeline(before,null);
            var fill=new GlProgram(read("fullscreen.vsh"),FIXTURE);var nowProbe=probe(source);var oldProbe=probe(before);var target=new GlTarget(GL_RGBA32F)){
            p.resize(48,.5f);old.resize(48,.5f);target.resize(W/2,H/2);p.glareEnabled=false;old.glareEnabled=false;fill.use();fill.scalar("SceneGain",1);
            float[] base=render(p,fill,false,0,false),baseLight=pixels(p.solution);image(p.cosmos,"scenery-no-dispersion");
            float[] prior=render(old,fill,true,0,false);image(old.cosmos,"scenery-before");
            float[] actual=render(p,fill,true,0,false);image(p.cosmos,"scenery-after");
            double oldDelta=difference(base,prior),newDelta=difference(base,actual);
            expect(newDelta>oldDelta*1.35,"surrounding scenery has visibly stronger dispersion; old="+oldDelta+" new="+newDelta);
            expect(Arrays.equals(baseLight,pixels(p.solution)),"wave/singularity emitted radiance is unchanged by spectrum enablement");
            p.spectrumEnabled=true;p.depth(1);old.depth(1);
            float[] now=sample(p,nowProbe,target,TICKS,VIEW),was=sample(old,oldProbe,target,TICKS,VIEW);
            int outside=0,wasOutside=0;double maxShift=0,oldShift=0;
            for(int i=0;i<now.length;i+=4){if(now[i+1]>.01 && now[i+2]>.5)outside++;if(was[i+1]>.01 && was[i+2]>.5)wasOutside++;maxShift=Math.max(maxShift,now[i]);oldShift=Math.max(oldShift,was[i]);}
            expect(outside>wasOutside*1.5,"scenery coverage OUTSIDE luminous sphere expands; before="+wasOutside+" after="+outside);
            expect(maxShift>oldShift*1.5 && maxShift<=80.001,"RGB offsets exaggerated but capped in full-resolution pixels; before="+oldShift+" after="+maxShift);
            for(int wall:new int[]{1,2}){
                base=render(p,fill,false,wall,false);actual=render(p,fill,true,wall,false);double protectedDelta=0,visibleDelta=0;
                for(int y=0;y<H;y++)for(int x=0;x<W;x++)for(int c=0;c<3;c++){double d=Math.abs(base[(y*W+x)*4+c]-actual[(y*W+x)*4+c]);if(wall==1 || x<W/2)protectedDelta=Math.max(protectedDelta,d);else visibleDelta+=d;}
                expect(protectedDelta==0,"foreground unchanged by dispersion, wall="+wall);if(wall==2)expect(visibleDelta>1,"exposed half retains scenery spectrum");
            }
            base=render(p,fill,false,0,true);actual=render(p,fill,true,0,true);expect(difference(base,actual)>.0001,"behind-glass scenery retains stronger spectrum");
            render(p,fill,true,0,false);float[] stable=pixels(p.cosmos);render(p,fill,true,0,false);expect(Arrays.equals(stable,pixels(p.cosmos)),"frozen timeline is pixel stable");
            for(Matrix4f view:new Matrix4f[]{VIEW,new Matrix4f().rotationX(1.5f),new Matrix4f().rotationY((float)Math.PI),new Matrix4f().translate(0,0,32)}){
                prepare(p,fill,0,false);finite(sample(p,nowProbe,target,TICKS,view),"finite spectrum at forward/up/back/exact-center camera");
                p.subject(930,view,0,-32,true);finite(pixels(p.cosmos),"expanded late wave remains finite inside/outside support");
            }
            for(int steps:new int[]{24,48,96}){p.resize(steps,steps==24?.375f:steps==96?.75f:.5f);finite(render(p,fill,true,0,false),"finite quality "+steps);}
            p.resize(48,.5f);
            p.projection.identity().perspective((float)Math.toRadians(70),W/(float)H,.05f,4096);
            for(float fov:new float[]{40,70,110}){
                p.projection.identity().perspective((float)Math.toRadians(fov),W/(float)H,.05f,4096);p.depth(1);now=sample(p,nowProbe,target,TICKS,VIEW);maxShift=0;
                for(int i=0;i<now.length;i+=4)maxShift=Math.max(maxShift,now[i]);expect(maxShift<=80.001,"pixel cap preserved at FOV "+fov);
            }
            p.projection.identity().perspective((float)Math.toRadians(70),W/(float)H,.05f,4096);p.spectrumEnabled=true;
            for(double tick:new double[]{836,900,940}){
                double priorMs=measure(()->{prepare(old,fill,0,false);old.subject(tick,VIEW,0,-32,true);});
                double nowMs=measure(()->{prepare(p,fill,0,false);p.subject(tick,VIEW,0,-32,true);});
                REPORT.append(String.format(Locale.ROOT,"tick %.0f: old shader %.3f ms, new %.3f ms, delta %+.3f ms (same expanded wave uniforms)%n",tick,priorMs,nowMs,nowMs-priorMs));
            }
            allWaves(p,old,fill);
            expect(glGetError()==GL_NO_ERROR,"GL error-free after all fixtures");
            int texture=target.textures[0],fbo=target.framebuffer;target.close();expect(!glIsTexture(texture)&&!glIsFramebuffer(fbo),"probe resources released");
            var board=new BufferedImage(1920,720,BufferedImage.TYPE_INT_RGB);var g=board.createGraphics();g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,22));
            String[] pictures={"scenery-before","scenery-after"};for(int n=0;n<2;n++){g.drawImage(ImageIO.read(output.resolve(pictures[n]+".png").toFile()),n*960,0,960,540,null);g.setColor(Color.WHITE);g.drawString(n==0?"BEFORE":"AFTER - SCENERY DISPERSION",n*960+20,580);}
            g.drawString("Identical wave radius, camera and time. Synthetic fixture; not a Minecraft screenshot.",20,635);g.dispose();ImageIO.write(board,"png",output.resolve("before-after.png").toFile());
        }finally{glDeleteVertexArrays(vao);glfwDestroyWindow(window);glfwTerminate();}
        Files.writeString(output.resolve("report.txt"),REPORT);System.out.println(REPORT);
    }
}
