package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static com.mega.overheaven.foliagecompat.blackhole.client.FinaleGpuVerification.*;
import java.nio.file.*;
import java.util.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;

/** Production GLSL in a hidden context; source/foreground fixtures, not Minecraft integration. */
public final class ShockwaveGpuVerification {
    private static final StringBuilder REPORT=new StringBuilder();
    private static final String FIXTURE="""
        #version 330 core
        in vec2 texCoord; layout(location=0) out vec4 Color;
        void main(){
            vec2 p=texCoord;
            float grid=step(.5,fract(p.x*64.))*step(.5,fract(p.y*36.));
            vec3 c=mix(vec3(.025,.045,.075),vec3(.16,.28,.38),p.y);
            c+=grid*vec3(.28,.20,.11);
            c+=exp(-pow((p.y-.46)*50.,2.))*vec3(.06,.12,.18);
            Color=vec4(c,1.);
        }
        """;
    private static final double START=160+360*.37, DURATION=
        com.mega.overheaven.foliagecompat.finale.FinaleWaves.end(
            com.mega.overheaven.foliagecompat.finale.FinaleTimeline.defaults(),1,3.2,11,.16)-START;
    private static final double LAUNCH_DURATION=com.mega.overheaven.foliagecompat.finale.FinaleWaves.duration(
        com.mega.overheaven.foliagecompat.finale.FinaleTimeline.defaults(),1);
    private static final Matrix4f VIEW=new Matrix4f();
    private static void expect(boolean value,String text){check(value,text);REPORT.append("PASS ").append(text).append('\n');}
    private static double maxDifference(float[] a,float[] b){double d=0;for(int i=0;i<a.length;i++)d=Math.max(d,Math.abs(a[i]-b[i]));return d;}
    private static double difference(float[] a,float[] b){double d=0;for(int i=0;i<a.length;i+=4)for(int c=0;c<3;c++)d+=Math.abs(a[i+c]-b[i+c]);return d/(a.length*.75);}
    private static void finite(float[] data,String label){for(float f:data)if(!Float.isFinite(f))throw new AssertionError(label);expect(true,label);}
    private static String gate(String source,String function,String code){
        int start=source.indexOf(function),body=source.indexOf('{',start);
        if(start<0 || body<0)throw new AssertionError(function);
        return source.substring(0,body+1)+code+source.substring(body+1);
    }
    private static String gated(String source){
        source=source.replace("#version 330 core","#version 330 core\nuniform int TestWaveDisabled;");
        source=gate(source,"void energyFront(","if(TestWaveDisabled==1)return;");
        if(source.contains("vec3 energyRefraction("))source=gate(source,"vec3 energyRefraction(","if(TestWaveDisabled==1)return vec3(uv,-1.);");
        return source;
    }
    private static GlProgram probe(Path resources,int mode)throws Exception {
        String shader="#version 330 core\nin vec2 texCoord;out vec4 Color;\n"+VerificationShaders.read(resources,"field.glsl");
        String body=mode==0?"Field f=finaleField(texCoord); Color=vec4(f.lens.xy,f.meta.w,1.);":
            "Field f=finaleField(texCoord); vec3 wave=energyRefraction(texCoord,f.lens.xy,f.meta.w,rayAt(texCoord),distanceAt(texCoord,texture(OpaqueDepth,texCoord).r)); Color=vec4(wave,1.);";
        shader+="\nvoid main(){"+body+"}";
        return new GlProgram(VerificationShaders.read(resources,"fullscreen.vsh"),gated(shader));
    }
    private static GlProgram lensOnly(Path resources)throws Exception {
        String shader=VerificationShaders.read(resources,"composite.fsh");
        shader=shader.substring(0,shader.lastIndexOf("void main()"));
        boolean current=shader.contains("vec3 energyRefraction(");
        shader+="""
            void main(){
                Field f=finaleField(texCoord);
                float opaque=distanceAt(texCoord,texture(OpaqueDepth,texCoord).r);
                float surface=distanceAt(texCoord,texture(SurfaceDepth,texCoord).r);
                bool transparent=surface<opaque-max(.002,opaque*.00001);
                vec3 original=transparent?texture(Opaque,texCoord).rgb:texture(Scene,texCoord).rgb;
            """;
        shader+="vec3 color=backgroundAt(texCoord,f.lens.xy,f.meta.w,f.meta.w>=0.?1.:0.,!transparent"+(current?",original":"")+");\n";
        if(current)shader+="vec3 w=energyRefraction(texCoord,f.lens.xy,f.meta.w,rayAt(texCoord),opaque); if(w.z>=0.)color=backgroundAt(texCoord,w.xy,w.z,1.,!transparent,color);\n";
        shader+="Color=vec4(color,1.);}";
        return new GlProgram(VerificationShaders.read(resources,"fullscreen.vsh"),gated(shader));
    }
    private static float[] sample(FinaleFlashGpuVerification.Pipeline p,GlProgram shader,GlTarget target,double ticks,Matrix4f view,boolean disabled){
        target.bind();GlState.fullscreen();shader.use();p.material(shader,ticks,view,0,-24,true,target.height);
        shader.integer("TestWaveDisabled",disabled?1:0);shader.texture("Scene",0,p.scene.textures[0]);shader.texture("Opaque",1,p.scene.textures[0]);
        draw();return pixels(target);
    }
    private static float depth(FinaleFlashGpuVerification.Pipeline p,float distance){var v=p.projection.transform(new Vector4f(0,0,-distance,1));return v.z/v.w*.5f+.5f;}
    private static void rectangle(GlTarget target,int attachment,int x,int y,int width,int height,float value){
        target.bind();glEnable(GL_SCISSOR_TEST);glScissor(x,y,width,height);glClearBufferfv(GL_COLOR,attachment,new float[]{value,0,0,0});glDisable(GL_SCISSOR_TEST);
    }
    private static void prepare(FinaleFlashGpuVerification.Pipeline p,GlProgram fixture){
        p.scene.bind();GlState.fullscreen();fixture.use();draw();p.blit(p.scene,p.cosmos,GL_NEAREST);p.depth(1);
    }
    private static void preservation(FinaleFlashGpuVerification.Pipeline p,FinaleFlashGpuVerification.Pipeline old,Path before)throws Exception {
        try(var now=probe(source,0);var previous=probe(before,0);var result=new GlTarget(GL_RGBA32F)){
            result.resize(W/4,H/4);p.depth(1);old.depth(1);
            double t=START+LAUNCH_DURATION*.42;
            var oldActive=sample(old,previous,result,t,VIEW,false);var oldBase=sample(old,previous,result,t,VIEW,true);
            double broken=maxDifference(oldActive,oldBase);
            expect(broken>.01,"old shader reproduces lens/guard overwrite; max delta="+broken);
            float[] ages={0,.04f,.09f,.15f,.4f,.6f,.82f,.95f,.999f,1};
            Matrix4f[] views={VIEW,new Matrix4f().rotationX(.7f),new Matrix4f().rotationY((float)Math.PI),
                new Matrix4f().rotationX(1.45f).translate(0,-6,24),new Matrix4f().translate(0,0,18)};
            for(int v=0;v<views.length;v++)for(float age:ages){
                t=START+DURATION*age;var active=sample(p,now,result,t,views[v],false);var base=sample(p,now,result,t,views[v],true);
                finite(active,"finite lens age="+age+" view="+v);
                expect(maxDifference(active,base)==0,"base lens and guard bit-identical with active wave age="+age+" view="+v);
            }
        }
    }
    private static void rejection(FinaleFlashGpuVerification.Pipeline p,GlProgram fixture)throws Exception {
        try(var warp=probe(source,1);var baseProbe=probe(source,0);var lens=lensOnly(source);var result=new GlTarget(GL_RGBA32F)){
            result.resize(W,H);prepare(p,fixture);double ticks=START+LAUNCH_DURATION*.42;
            float[] wave=sample(p,warp,result,ticks,VIEW,false),base=sample(p,baseProbe,result,ticks,VIEW,false);
            int chosen=-1;
            for(int y=H/8;y<H*7/8 && chosen<0;y++)for(int x=W/8;x<W*7/8;x++){
                int i=(y*W+x)*4;float dx=(wave[i]-base[i])*W,dy=(wave[i+1]-base[i+1])*H;
                float receiverX=wave[i]*W-(x+.5f),receiverY=wave[i+1]*H-(y+.5f);
                // Keep the injected source glass away from the receiver itself. Otherwise
                // this fixture changes the receiver to a resolved transparent layer.
                if(wave[i+2]>0 && base[i+2]>0 && dx*dx+dy*dy>64 && receiverX*receiverX+receiverY*receiverY>64 && wave[i]>.08 && wave[i]<.92 && wave[i+1]>.08 && wave[i+1]<.92){chosen=i;break;}
            }
            expect(chosen>=0,"wave has measurable angular refraction over existing gravitational mapping");
            int sx=(int)(wave[chosen]*W),sy=(int)(wave[chosen+1]*H);
            var active=sample(p,lens,result,ticks,VIEW,false);image(result,"lens-wave");
            var noWave=sample(p,lens,result,ticks,VIEW,true);image(result,"lens-baseline");
            expect(difference(active,noWave)>.00001,"wave refraction visible in actual background sample");
            int cx=W/2,cy=H/2+H/12;int inside=(cy*W+cx)*4;
            expect(Math.abs(active[inside]-noWave[inside])<.00001,"wave interior retains lens instead of restoring identity");
            for(int mode=0;mode<2;mode++){
                p.depth(1);rectangle(mode==0?p.opaque:p.surface,1,sx-2,sy-2,5,5,depth(p,2));
                var rejected=sample(p,lens,result,ticks,VIEW,false);var fallback=sample(p,lens,result,ticks,VIEW,true);
                double delta=0;for(int c=0;c<3;c++)delta=Math.max(delta,Math.abs(rejected[chosen+c]-fallback[chosen+c]));
                expect(delta<.00001,(mode==0?"foreground":"unresolved source glass")+" rejects extra bend back to gravitational image");
            }
            p.depth(depth(p,8));active=sample(p,lens,result,ticks,VIEW,false);noWave=sample(p,lens,result,ticks,VIEW,true);
            expect(maxDifference(active,noWave)==0,"wall before wave rejects all extra refraction");
            p.depth(1);rectangle(p.opaque,1,0,0,W/2,H,depth(p,8));active=sample(p,lens,result,ticks,VIEW,false);noWave=sample(p,lens,result,ticks,VIEW,true);
            double blocked=0;for(int y=0;y<H;y++)for(int x=0;x<W/2;x++)for(int c=0;c<3;c++)blocked=Math.max(blocked,Math.abs(active[(y*W+x)*4+c]-noWave[(y*W+x)*4+c]));
            expect(blocked==0,"partial foreground wall remains unchanged");
            p.depth(1);rectangle(p.surface,1,0,0,W,H,depth(p,8));active=sample(p,lens,result,ticks,VIEW,false);noWave=sample(p,lens,result,ticks,VIEW,true);
            expect(difference(active,noWave)>.00001,"transparent receiver retains background refraction behind its surface");
            p.depth(1);active=sample(p,lens,result,ticks,VIEW,false);noWave=sample(p,lens,result,ticks,VIEW,false);
            expect(maxDifference(active,noWave)==0,"paused fixed-time refraction is deterministic");
            for(var view:new Matrix4f[]{new Matrix4f().rotationY(.6f),new Matrix4f().rotationX(1.45f).translate(0,-6,24),new Matrix4f().translate(0,0,18)})
                finite(sample(p,lens,result,ticks,view,false),"screen edge / inside / upward composite finite");
        }
    }
    private static void motion()throws Exception {
        String shader="#version 330 core\nin vec2 texCoord;out vec4 Color;uniform float Age;\n"+VerificationShaders.read(source,"field.glsl")+"\nvoid main(){Color=energyWave(Age);}";
        try(var program=new GlProgram(read("fullscreen.vsh"),shader);var result=new GlTarget(GL_RGBA32F)){
            result.resize(1,1);program.use();program.scalar("Radius",3.2);program.scalar("Outer",11);program.scalar("Thickness",.12);
            float previous=0;
            double chargeEnd=com.mega.overheaven.foliagecompat.finale.FinaleWaves.duration(
                com.mega.overheaven.foliagecompat.finale.FinaleTimeline.defaults(),1)*.09/DURATION;
            for(int i=0;i<=100;i++){
                float age=i/100f;result.bind();program.use();program.scalar("Age",age);
                var wave=new com.mega.overheaven.foliagecompat.finale.FinaleWaves.State();
                com.mega.overheaven.foliagecompat.finale.FinaleWaves.sample(com.mega.overheaven.foliagecompat.finale.FinaleTimeline.defaults(),1,START+DURATION*age,3.2,11,.12,wave);
                FinaleWaveUniforms.apply(program,wave);draw();float[] state=pixels(result);
                if(i==1)expect(state[0]<previous,"prelaunch compression reduces radius");
                if(age>chargeEnd+.01)expect(state[0]>previous,"outward radius advances through age="+age);
                expect(state[0]+state[3]*.12<=32,"head and outgoing fragments stay inside renderer support");
                previous=state[0];
            }
        }
    }
    private static void emission(FinaleFlashGpuVerification.Pipeline p)throws Exception {
        try(var emit=new GlProgram(read("fullscreen.vsh"),gated("#version 330 core\nin vec2 texCoord;out vec4 Color;uniform int Segment;\n"+read("field.glsl")+"\nvoid main(){Field f;f.front=vec4(0,0,0,1);f.back=f.front;f.lens=vec4(texCoord,65504,65504);f.meta=vec4(0);float opaque=distanceAt(texCoord,texture(OpaqueDepth,texCoord).r),surface=distanceAt(texCoord,texture(SurfaceDepth,texCoord).r);energyFront(f,Eye-Center,rayAt(texCoord),opaque,surface);Color=Segment==0?f.front:f.back;}"));var target=new GlTarget(GL_RGBA32F)){
            target.resize(W/2,H/2);double ticks=START+LAUNCH_DURATION*.42;p.depth(1);
            emit.use();emit.integer("Segment",0);var with=sample(p,emit,target,ticks,VIEW,false);var without=sample(p,emit,target,ticks,VIEW,true);
            double reference=difference(with,without);expect(reference>.00001,"wave emits into front segment");
            rectangle(p.surface,1,0,0,W,H,depth(p,8));with=sample(p,emit,target,ticks,VIEW,false);without=sample(p,emit,target,ticks,VIEW,true);
            expect(maxDifference(with,without)==0,"glass in front keeps wave out of front radiance");
            emit.use();emit.integer("Segment",1);with=sample(p,emit,target,ticks,VIEW,false);without=sample(p,emit,target,ticks,VIEW,true);
            expect(Math.abs(difference(with,without)-reference)<.000001,"wave radiance moves intact to behind-glass segment");
            p.depth(depth(p,8));with=sample(p,emit,target,ticks,VIEW,false);without=sample(p,emit,target,ticks,VIEW,true);
            expect(maxDifference(with,without)==0,"opaque wall blocks wave emission");
            p.depth(1);emit.use();emit.integer("Segment",0);float[] referencePixels=null;
            for(int steps:new int[]{24,48,96}){p.steps=steps;with=sample(p,emit,target,ticks,VIEW,false);if(referencePixels==null)referencePixels=with;expect(maxDifference(referencePixels,with)==0,"analytic wave brightness independent of "+steps+" sample budget");}
            p.steps=48;
            for(var view:new Matrix4f[]{VIEW,new Matrix4f().rotationX(.7f),new Matrix4f().rotationY((float)Math.PI),new Matrix4f().rotationX(1.45f).translate(0,-6,24),new Matrix4f().translate(0,0,24)})
                finite(sample(p,emit,target,ticks,view,false),"wave radiance finite including camera at exact center");
        }
    }
    private static void render(FinaleFlashGpuVerification.Pipeline p,GlProgram fixture,double age){
        prepare(p,fixture);p.subject(START+DURATION*age,VIEW,0,-24,true);
    }
    private static double measure(FinaleFlashGpuVerification.Pipeline p,GlProgram fixture,double age){
        for(int i=0;i<5;i++)render(p,fixture,age);int query=glGenQueries();glBeginQuery(GL_TIME_ELAPSED,query);
        for(int i=0;i<24;i++)render(p,fixture,age);glEndQuery(GL_TIME_ELAPSED);double ms=glGetQueryObjectui64(query,GL_QUERY_RESULT)/24e6;glDeleteQueries(query);return ms;
    }
    private static void pictures(FinaleFlashGpuVerification.Pipeline p,FinaleFlashGpuVerification.Pipeline old,GlProgram fixture)throws Exception {
        double[] ages={.06,.16,.36,.58,.77,.91};var board=new BufferedImage(960*ages.length,540*2,BufferedImage.TYPE_INT_RGB);var graphics=board.createGraphics();
        graphics.setFont(new Font(Font.SANS_SERIF,Font.BOLD,22));
        for(int n=0;n<ages.length;n++)for(int row=0;row<2;row++){
            var pipeline=row==0?old:p;render(pipeline,fixture,ages[n]);String name=(row==0?"before-":"after-")+n;image(pipeline.cosmos,name);
            var img=ImageIO.read(output.resolve(name+".png").toFile());graphics.drawImage(img,n*960,row*540,960,540,null);graphics.setColor(Color.WHITE);graphics.drawString((row==0?"BEFORE":"AFTER")+" age="+ages[n],n*960+20,row*540+32);
        }
        graphics.dispose();ImageIO.write(board,"png",output.resolve("wave-comparison.png").toFile());
        for(double age:new double[]{-.04,.16,.42,.72,.91,1.04}){
            double[] a=new double[3],b=new double[3];for(int trial=0;trial<3;trial++){a[trial]=measure(old,fixture,age);b[trial]=measure(p,fixture,age);}Arrays.sort(a);Arrays.sort(b);
            REPORT.append(String.format(Locale.ROOT,"GPU age=%.2f before=%.3f after=%.3f delta=%+.3f ms (median of 3 x 24 frames)%n",age,a[1],b[1],b[1]-a[1]));
        }
    }
    private static void preview(FinaleFlashGpuVerification.Pipeline p,FinaleFlashGpuVerification.Pipeline old,GlProgram fixture,String encoder)throws Exception {
        var process=new ProcessBuilder(encoder,"-y","-f","rawvideo","-pix_fmt","bgr24","-s",W+"x"+(H/2),"-r","30","-i","pipe:0","-an","-c:v","libx264","-preset","fast","-crf","19","-pix_fmt","yuv420p","-movflags","+faststart",output.resolve("shockwave-before-after.mp4").toString())
            .redirectErrorStream(true).redirectOutput(output.resolve("preview-encoder.log").toFile()).start();
        var image=new BufferedImage(W,H/2,BufferedImage.TYPE_3BYTE_BGR);
        byte[] imageBytes=((java.awt.image.DataBufferByte)image.getRaster().getDataBuffer()).getData();
        var buffer=BufferUtils.createByteBuffer(W/2*H/2*3);var graphics=image.createGraphics();graphics.setFont(new Font(Font.SANS_SERIF,Font.BOLD,24));
        try(var small=new GlTarget(GL_RGBA16F);var pipe=process.getOutputStream()){
            small.resize(W/2,H/2);
            for(int frame=0;frame<144;frame++){
                double age=-.06+1.18*frame/143;
                for(int column=0;column<2;column++){
                    var pipeline=column==0?old:p;render(pipeline,fixture,age);pipeline.blit(pipeline.cosmos,small,GL_LINEAR);small.bind();glReadBuffer(GL_COLOR_ATTACHMENT0);buffer.clear();glReadPixels(0,0,W/2,H/2,GL_RGB,GL_UNSIGNED_BYTE,buffer);
                    for(int y=0;y<H/2;y++)for(int x=0;x<W/2;x++){
                        int from=(y*W/2+x)*3,to=((H/2-1-y)*W+column*W/2+x)*3;
                        imageBytes[to]=buffer.get(from+2);imageBytes[to+1]=buffer.get(from+1);imageBytes[to+2]=buffer.get(from);
                    }
                }
                graphics.setColor(new Color(0,0,0,180));graphics.fillRect(0,0,W,48);graphics.setColor(Color.WHITE);
                graphics.drawString(String.format(Locale.ROOT,"BEFORE  age %.2f",age),20,33);graphics.drawString(String.format(Locale.ROOT,"AFTER  age %.2f",age),W/2+20,33);pipe.write(imageBytes);
            }
        }finally{graphics.dispose();}
        if(process.waitFor()!=0)throw new AssertionError("preview encoder failed");
        REPORT.append("Preview: 144 rendered frames, 30fps, muted, fixed world camera/seed.\n");
    }
    public static void main(String[] args)throws Exception {
        source=Path.of(args[0]);output=Path.of(args[1]);Path before=Path.of(args[2]);Files.createDirectories(output);
        expect(glfwInit(),"GLFW");glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
        long window=glfwCreateWindow(64,64,"shockwave regression",0,0);expect(window!=0,"hidden context");glfwMakeContextCurrent(window);GL.createCapabilities();vao=glGenVertexArrays();GlState.fullscreen();
        REPORT.append("GPU ").append(glGetString(GL_RENDERER)).append("; 1920x1080, 960x540 solve, balanced. Synthetic fixture + solve + bloom + composition; not Minecraft frame time.\n");
        try(var p=new FinaleFlashGpuVerification.Pipeline();var old=new FinaleFlashGpuVerification.Pipeline(before,null);var fixture=new GlProgram(read("fullscreen.vsh"),FIXTURE)){
            p.glareEnabled=false;old.glareEnabled=false;
            p.resize(48,.5f);old.resize(48,.5f);prepare(p,fixture);prepare(old,fixture);
            expect(Files.readString(source.resolve("field.glsl")).equals(Files.readString(before.resolve("field.glsl"))),"singularity, membranes, ordinary lens and field unchanged verbatim");
            preservation(p,old,before);rejection(p,fixture);motion();emission(p);pictures(p,old,fixture);
            if(args.length>3)preview(p,old,fixture,args[3]);
            expect(glGetError()==GL_NO_ERROR,"GL error-free");
        }finally{Files.writeString(output.resolve("report.txt"),REPORT.toString());glDeleteVertexArrays(vao);glfwDestroyWindow(window);glfwTerminate();}
        System.out.println(REPORT+"Checks: "+checks);
    }
}
