package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static com.mega.overheaven.foliagecompat.blackhole.client.FinaleGpuVerification.*;
import com.mega.overheaven.foliagecompat.finale.FinaleTimeline;
import java.nio.file.*;
import java.util.*;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.BufferUtils;

/** A/B production GLSL, every phase, actual shard draws and equal-work GPU timers. */
public final class FinaleReimaginedGpuVerification {
    private static final FinaleTimeline TIMELINE=FinaleTimeline.defaults();
    private static final StringBuilder REPORT=new StringBuilder();
    private static void expect(boolean pass,String label) { check(pass,label); REPORT.append("PASS ").append(label).append('\n'); }
    private static double energy(float[] values) { double result=0; for(int i=0;i<values.length;i+=4) for(int c=0;c<3;c++) { if(!Float.isFinite(values[i+c])) throw new AssertionError("nonfinite pixel"); result+=values[i+c]; } return result; }
    private static String section(String text,String from,String to) { return text.substring(text.indexOf(from),text.indexOf(to,text.indexOf(from))).trim(); }
    public static void main(String[] args) throws Exception {
        source=Path.of(args[0]); output=Path.of(args[1]); Files.createDirectories(output);
        Path before=Path.of(args[2]),pack=Path.of(args[3]);
        String previous=Files.readString(before.resolve("field.glsl")).replace("\r\n","\n"),current=Files.readString(source.resolve("field.glsl")).replace("\r\n","\n");
        expect(section(previous,"float finaleRadius()","Field finaleField").equals(section(current,"float finaleRadius()","#include \"finale_energy.glsl\"")),"singularity radius, fade, film palette, porous veil and center bubbles preserved verbatim");
        expect(section(previous,"    float closest=-dot(origin,direction),impact=","    if(FinalePhase==0)").equals(section(current,"    float closest=-dot(origin,direction),impact=","    vec2 bounds=sphere(origin,direction,Outer*1.35")),"singularity focus, corona and pulse preserved verbatim");
        expect(previous.substring(0,previous.indexOf("float finaleRadius()")).equals(current.substring(0,current.indexOf("float finaleRadius()"))),"ordinary black-hole field, lens and material unchanged");
        expect(glfwInit(),"GLFW initialized"); glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE); glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
        long window=glfwCreateWindow(64,64,"finale reimagined verification",0,0); expect(window!=0,"hidden context"); glfwMakeContextCurrent(window); GL.createCapabilities(); vao=glGenVertexArrays();
        REPORT.append("GPU: ").append(glGetString(GL_RENDERER)).append("; 1920x1080 output, balanced half-resolution solve; synthetic fixture, no Minecraft or host postprocessing\n");
        String background="#version 330 core\nin vec2 texCoord;out vec4 Color;void main(){float line=step(.965,fract(texCoord.x*12.))+step(.95,fract(texCoord.y*8.));vec3 base=mix(vec3(.075,.10,.14),vec3(.15,.16,.18),smoothstep(.1,.9,texCoord.y));Color=vec4(base+line*.022,1);}";
        try(var old=new FinaleFlashGpuVerification.Pipeline(before,null);var now=new FinaleFlashGpuVerification.Pipeline();var fill=new GlProgram(read("fullscreen.vsh"),background)) {
            old.resize(48,.5f); now.resize(48,.5f); GlState.fullscreen();
            double[] seconds={.15,16.5,37.8,40.95,41.4,51.5,75.5,96,105.6,110.5,114.5,118};
            for(int index=0;index<seconds.length;index++) {
                double ticks=seconds[index]*20;
                for(int variant=0;variant<2;variant++) {
                    var pipeline=variant==0?old:now; render(pipeline,fill,ticks); energy(pixels(pipeline.result));
                    image(pipeline.result,String.format(Locale.ROOT,"%02d-%06.2fs-%s",index,seconds[index],variant==0?"before":"after"));
                }
                if(TIMELINE.black(TIMELINE.frame(ticks))) expect(energy(pixels(now.result))==0,"exact blackout at "+seconds[index]);
            }
            REPORT.append("Per-phase median GPU ms, five alternating batches of 16 frames after 200 warmup draws, identical fixture/camera/source parameters:\n");
            for(double secondsAt:new double[]{3,16.5,37.8,51.5,75.5,96,110.5,118}) {
                double[] costs=new double[2]; double[][] batches=new double[2][5];
                for(int warm=0;warm<100;warm++) { render(old,fill,secondsAt*20); render(now,fill,secondsAt*20); }
                for(int batch=0;batch<5;batch++) for(int slot=0;slot<2;slot++) {
                    int variant=(batch+slot)%2; var pipeline=variant==0?old:now;
                    int query=glGenQueries(); glBeginQuery(GL_TIME_ELAPSED,query);
                    for(int frame=0;frame<16;frame++) render(pipeline,fill,secondsAt*20);
                    glEndQuery(GL_TIME_ELAPSED); batches[variant][batch]=glGetQueryObjectui64(query,GL_QUERY_RESULT)/16e6; glDeleteQueries(query);
                }
                for(int variant=0;variant<2;variant++) { Arrays.sort(batches[variant]); costs[variant]=batches[variant][2]; }
                REPORT.append(String.format(Locale.ROOT,"%s %.2fs before %.3f after %.3f delta %+.3f ms%n",TIMELINE.frame(secondsAt*20).phase().id(),secondsAt,costs[0],costs[1],costs[1]-costs[0]));
            }
            String actual=PackSkySource.load(pack,Map.of(),read("finale_pack_stars.fsh"));
            try(var host=new FinaleFlashGpuVerification.Pipeline(source,actual)) {
                host.resize(48,.5f); render(host,fill,1510); expect(energy(pixels(host.background))>0,"actual Complementary star source plus non-star cosmic gas"); image(host.result,"complementary-cosmos");
                host.stars.use(); host.stars.integer("StarsOnly",1); // source switch is independently covered by verifySingularityGpu
            }
            for(int quality:new int[]{24,96,48}) {
                now.resize(quality,quality==24?.375f:quality==96?.75f:.5f); render(now,fill,1510); expect(energy(pixels(now.result))>0,"all show layers survive quality switch "+quality);
            }
            for(int direction=0;direction<4;direction++) {
                var view=new Matrix4f(); float distance=-24;
                if(direction==1)view.rotateY((float)Math.PI); if(direction==2)view.rotateX(-1.55f); if(direction==3)distance=-1;
                now.cosmos(1510,view,0,distance,false); expect(energy(pixels(now.cosmos))>0,"background and source survive real-view case "+direction); image(now.cosmos,"view-"+direction);
            }
            glass(now);
            if(args.length>4) preview(now,fill,args[4]);
            expect(glGetError()==GL_NO_ERROR,"GL error-free after all phases and geometry");
        } finally { glDeleteVertexArrays(vao); glfwDestroyWindow(window); glfwTerminate(); }
        REPORT.append("Checks passed: ").append(checks).append("\nScreenshots are GPU fixtures, not game captures. No packaged artifact created.\n");
        Files.writeString(output.resolve("report.txt"),REPORT); System.out.println(REPORT);
    }
    private static void render(FinaleFlashGpuVerification.Pipeline pipeline,GlProgram fill,double ticks) {
        GlState.fullscreen(); pipeline.cosmos.bind(); fill.use(); draw(); pipeline.blit(pipeline.cosmos,pipeline.world,GL_NEAREST);
        if(TIMELINE.takeover(ticks)<1) { pipeline.depth(1); pipeline.subject(ticks,new Matrix4f(),0,-24,true); pipeline.blit(pipeline.cosmos,pipeline.world,GL_NEAREST); }
        if(TIMELINE.takeover(ticks)>0) pipeline.cosmos(ticks,new Matrix4f(),0,-24,false);
        pipeline.compose(ticks,-1);
    }
    private static void preview(FinaleFlashGpuVerification.Pipeline pipeline,GlProgram fill,String encoder) throws Exception {
        Path movie=output.resolve("finale-preview-4x.mp4");
        Process process=new ProcessBuilder(encoder,"-v","error","-y","-f","rawvideo","-pixel_format","rgba","-video_size",W+"x"+H,"-framerate","12","-i","pipe:0","-vf","vflip,scale=1280:720","-c:v","libx264","-preset","veryfast","-crf","20","-pix_fmt","yuv420p","-movflags","+faststart",movie.toString()).redirectError(output.resolve("preview-encoder.log").toFile()).start();
        var buffer=BufferUtils.createByteBuffer(W*H*4); byte[] bytes=new byte[W*H*4];
        try(var stream=process.getOutputStream()) {
            for(int frame=0;frame<360;frame++) {
                render(pipeline,fill,frame*80.0/12); pipeline.result.bind(); glReadBuffer(GL_COLOR_ATTACHMENT0);
                buffer.clear(); glReadPixels(0,0,W,H,GL_RGBA,GL_UNSIGNED_BYTE,buffer); buffer.get(bytes); stream.write(bytes);
            }
        } finally {
            if(!process.waitFor(60,java.util.concurrent.TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IllegalStateException("preview encoder timeout"); }
        }
        expect(process.exitValue()==0 && Files.size(movie)>0,"30-second muted 4x timeline preview encoded from production GLSL");
    }
    private static int texture(int width,int height,float[] values) {
        int id=glGenTextures(); glBindTexture(GL_TEXTURE_2D,id); var data=BufferUtils.createFloatBuffer(values.length); data.put(values).flip();
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,width,height,0,GL_RGBA,GL_FLOAT,data); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST); return id;
    }
    private static void glass(FinaleFlashGpuVerification.Pipeline pipeline) throws Exception {
        int count=36; float[] points=new float[count*4],boxes=new float[count*8];
        for(int i=0;i<count;i++) { float x=(i%6-2.5f)*1.1f,y=(i/6-2.5f)*1.1f; points[i*4]=boxes[i*8]=x; points[i*4+1]=boxes[i*8+1]=y; points[i*4+2]=boxes[i*8+2]=8; boxes[i*8+4]=boxes[i*8+5]=.5f; boxes[i*8+6]=.05f; }
        int positions=texture(count,1,points),shapes=texture(2,count,boxes);
        try(var program=new GlProgram(read("finale_geometry.vsh"),read("finale_geometry.fsh"));var target=new GlTarget(GL_RGBA16F,GL_RGBA16F,GL_RGBA32F)) {
            target.resize(W,H); pipeline.depth(1);
            for(int stage=0;stage<4;stage++) {
                target.bind(); for(int i=0;i<3;i++)glClearBufferfv(GL_COLOR,i,new float[4]);
                program.use(); uniforms(program,stage<2?2:3,stage==0?.73f:stage==1?.86f:stage==2?.32f:.70f,stage<2?775:1010,new Matrix4f());
                program.scalar("Visibility",1); program.pair("ScreenSize",W,H); program.integer("GlassCount",count); program.integer("GlassFragments",count*3); program.integer("Mode",stage==0?0:1);
                program.texture("Positions",0,positions); program.texture("Shapes",1,shapes); program.texture("OpaqueDepth",8,pipeline.opaque.textures[1]); program.texture("SurfaceDepth",9,pipeline.surface.textures[1]);
                glBindVertexArray(vao); glDrawArraysInstanced(GL_TRIANGLES,0,stage==0?36:6,stage==0?count:count*3);
                double visible=energy(pixels(target)); expect(visible>0,"real geometry rasterization stage="+stage); image(target,"glass-"+stage);
                var frozen=pixels(target); glDrawArraysInstanced(GL_TRIANGLES,0,stage==0?36:6,stage==0?count:count*3); expect(Arrays.equals(frozen,pixels(target)),"frozen geometry deterministic "+stage);
                pipeline.depth(.9f); target.bind(); for(int i=0;i<3;i++)glClearBufferfv(GL_COLOR,i,new float[4]); program.use(); glDrawArraysInstanced(GL_TRIANGLES,0,stage==0?36:6,stage==0?count:count*3);
                expect(energy(pixels(target))==0,"foreground depth blocks cracks/shards "+stage); pipeline.depth(1);
            }
        } finally { glDeleteTextures(positions); glDeleteTextures(shapes); }
    }
}
