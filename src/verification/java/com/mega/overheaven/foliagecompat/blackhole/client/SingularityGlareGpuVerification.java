package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static com.mega.overheaven.foliagecompat.blackhole.client.FinaleGpuVerification.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;

public final class SingularityGlareGpuVerification {
    private static final StringBuilder REPORT=new StringBuilder();
    private static void expect(boolean passed,String label) { check(passed,label); REPORT.append("PASS ").append(label).append('\n'); }
    private static double maximum(float[] values) { double maximum=0; for(int index=0;index<values.length;index+=4) for(int channel=0;channel<3;channel++) maximum=Math.max(maximum,values[index+channel]); return maximum; }
    private static double difference(float[] first,float[] second) { double maximum=0; for(int index=0;index<first.length;index++) maximum=Math.max(maximum,Math.abs(first[index]-second[index])); return maximum; }
    private static void finite(float[] values,String label) { for(float value:values) if(!Float.isFinite(value)) throw new AssertionError(label); expect(true,label); }
    private static void errorFree(String label) { int error=glGetError(); expect(error==GL_NO_ERROR,label+" GL error="+error); }
    private static float[] attachment(GlTarget target,int attachment) {
        target.bind(); glReadBuffer(GL_COLOR_ATTACHMENT0+attachment); var buffer=BufferUtils.createFloatBuffer(target.width*target.height*4);
        glReadPixels(0,0,target.width,target.height,GL_RGBA,GL_FLOAT,buffer); float[] values=new float[buffer.remaining()]; buffer.get(values); return values;
    }
    private static void fill(GlTarget target,float red,float green,float blue,float alpha) { target.bind(); glDisable(GL_SCISSOR_TEST); glClearBufferfv(GL_COLOR,0,new float[]{red,green,blue,alpha}); }
    private static void rectangle(GlTarget target,int left,int bottom,int width,int height,float value) {
        target.bind(); glEnable(GL_SCISSOR_TEST); glScissor(left,bottom,width,height); glClearBufferfv(GL_COLOR,0,new float[]{value,0,0,0}); glDisable(GL_SCISSOR_TEST);
    }
    static final class Fixture implements AutoCloseable {
        final GlTarget opaque=new GlTarget(GL_R32F),surface=new GlTarget(GL_R32F),transmission=new GlTarget(GL_RGBA16F),result=new GlTarget(GL_RGBA32F);
        final GlSingularityGlare glare=new GlSingularityGlare(read("fullscreen.vsh"),read("singularity_source.fsh"));
        final GlProgram compose;
        final Matrix4f projection=new Matrix4f(),view=new Matrix4f(),matrix=new Matrix4f(),inverse=new Matrix4f();
        final Vector4f eye=new Vector4f();
        double revealPulse;
        int width=960,height=540,phase=1,sourceDraws; float distance=64,radius=3.2f,glow=.7f,brightness=3.2f,amount=1,progress=.5f,fov=70;
        Fixture() throws Exception {
            String prefix="#version 330 core\nin vec2 texCoord;out vec4 Color;uniform int LayerMode;uniform sampler2D Transmission;\n";
            compose=new GlProgram(read("fullscreen.vsh"),prefix+read("field.glsl")+read("singularity_glare.glsl")+"\nvoid main(){float opaque=distanceAt(texCoord,texture(OpaqueDepth,texCoord).r),surface=distanceAt(texCoord,texture(SurfaceDepth,texCoord).r);bool transparent=surface<opaque-max(.002,opaque*.00001);Color=vec4(singularityGlare(texCoord,opaque,surface,transparent),1.);}");
            resize(width,height);
        }
        void resize(int width,int height) { this.width=width;this.height=height;opaque.resize(width,height);surface.resize(width,height);transmission.resize(width,height);result.resize(width,height);clear(); }
        void matrices() {
            projection.identity().perspective((float)Math.toRadians(fov),width/(float)height,.05f,4096);matrix.set(projection).mul(view);inverse.set(matrix).invert();inverse.transform(eye.set(0,0,1,0));eye.div(eye.w);
        }
        void uniforms(GlProgram program) {
            program.matrix("VP",matrix);program.matrix("InverseVP",inverse);program.vector("Eye",eye.x,eye.y,eye.z);program.vector("Center",0,0,-distance);
            program.scalar("Radius",radius);program.scalar("PixelAngle",2.0/(height*projection.m11()));program.scalar("FinaleAmount",amount);program.integer("FinalePhase",phase);program.scalar("FinaleProgress",progress);
            program.texture("OpaqueDepth",8,opaque.textures[0]);program.texture("SurfaceDepth",9,surface.textures[0]);program.texture("Transmission",2,transmission.textures[0]);
        }
        void clear() {fill(opaque,1,0,0,0);fill(surface,1,0,0,0);fill(transmission,1,1,1,1);}
        float depth(float distance) {matrices();var point=projection.transform(new Vector4f(0,0,-distance,1));return point.z/point.w*.5f+.5f;}
        boolean prepare() {
            matrices();boolean active=glare.project(matrix,eye,0,0,-distance,radius,amount,glow,brightness,phase,progress,width,height,revealPulse);
            if(active) {var program=glare.begin();uniforms(program);draw();sourceDraws++;}return active;
        }
        float[] shade(int layer) {result.bind();GlState.fullscreen();compose.use();uniforms(compose);compose.integer("LayerMode",layer);glare.bind(compose);draw();return pixels(result);}
        float[] render(int layer) {prepare();return shade(layer);}
        @Override public void close(){glare.close();compose.close();opaque.close();surface.close();transmission.close();result.close();}
    }
    private static void distances(Fixture fixture)throws Exception {
        double last=0;int initialDraws=fixture.sourceDraws;
        for(float distance:new float[]{8,16,24,32,48,64,128}) {
            fixture.distance=distance;var values=fixture.render(0);double peak=maximum(values);finite(values,"distance "+distance+" finite");
            if(distance<=16)expect(peak==0 && fixture.sourceDraws==initialDraws,"near distance "+distance+" produces zero light and no source draw");
            else if(distance<=64)expect(peak>last,"distance gain rises at "+distance);
            else expect(Math.abs(peak-last)<1.e-6,"distance gain saturates beyond 64 blocks");
            last=peak;image(fixture.result,"glare-only-distance-"+(int)distance);
        }
        fixture.distance=64;var standard=fixture.render(0);fixture.radius=6.4f;fixture.distance=128;var enlarged=fixture.render(0);
        expect(difference(standard,enlarged)<.00001,"gain and arm size scale with base radius");
        fixture.distance=32;expect(maximum(fixture.render(0))==0,"scaled near range remains exactly zero");
        fixture.radius=3.2f;fixture.distance=64;
        for(float fov:new float[]{30,70,110}) {fixture.fov=fov;expect(difference(standard,fixture.render(0))<.00001,"FOV "+fov+" does not alter distance gain or pixel arms");}
        fixture.fov=70;
        for(int[] size:new int[][]{{960,540},{540,960},{768,768},{1280,720}}) {
            fixture.resize(size[0],size[1]);float[] values=fixture.render(0);int centerX=fixture.width/2,centerY=fixture.height/2;
            double error=0;for(int offset=1;offset<(int)(Math.min(fixture.width,fixture.height)*.22);offset++) {
                float east=values[(centerY*fixture.width+centerX+offset)*4],north=values[((centerY+offset)*fixture.width+centerX)*4];
                float west=values[(centerY*fixture.width+centerX-offset-1)*4],south=values[((centerY-offset-1)*fixture.width+centerX)*4];
                error=Math.max(error,Math.max(Math.abs(east-north),Math.max(Math.abs(east-west),Math.abs(east-south))));
            }
            expect(error<.0001,"four equal pixel-space arms at "+fixture.width+"x"+fixture.height);
            int outside=Math.min(fixture.width-1,centerX+(int)Math.ceil(Math.min(fixture.width,fixture.height)*.22)+1);
            expect(values[(centerY*fixture.width+outside)*4]==0,"finite arm support at "+fixture.width+"x"+fixture.height);
        }
        fixture.resize(960,540);fixture.distance=64;double normalPeak=maximum(fixture.render(0));fixture.revealPulse=1;
        expect(Math.abs(maximum(fixture.render(0))/normalPeak-2)<.0001,"reveal peak exactly twice fully distant glare");
        fixture.distance=8;
        expect(maximum(fixture.render(0))>0,"near reveal pulse bypasses distance gate");
        fixture.glow=0;expect(maximum(fixture.render(0))==0,"reveal pulse obeys glow zero");fixture.glow=.7f;
        fill(fixture.opaque,fixture.depth(2),0,0,0);expect(maximum(fixture.render(0))==0,"near reveal pulse blocked by wall");fixture.clear();
        fixture.revealPulse=0;expect(maximum(fixture.render(0))==0,"near glare returns to exact zero after pulse");
    }
    private static void visibility(Fixture fixture)throws Exception {
        fixture.distance=64;fixture.clear();float[] original=fixture.render(0);
        fill(fixture.opaque,fixture.depth(8),0,0,0);expect(maximum(fixture.render(0))==0,"whole foreground wall blocks glare");
        fixture.clear();rectangle(fixture.opaque,fixture.width/2-5,fixture.height/2-5,10,10,fixture.depth(8));
        expect(maximum(fixture.render(0))==0,"wall only over emitter suppresses every arm");
        fixture.clear();rectangle(fixture.opaque,fixture.width/2,0,fixture.width/2,fixture.height,fixture.depth(8));
        float[] partial=fixture.render(0),front=attachment(fixture.glare.source,0);
        expect(front[0]>0 && front[0]<1,"nine samples give fractional source visibility at wall edge");
        double foreground=0;for(int row=0;row<fixture.height;row++)for(int column=fixture.width/2;column<fixture.width;column++)foreground=Math.max(foreground,partial[(row*fixture.width+column)*4]);
        expect(foreground==0 && maximum(partial)>0,"partial wall clips destination without erasing visible side");
        fixture.clear();rectangle(fixture.opaque,fixture.width/2+40,0,12,fixture.height,fixture.depth(8));partial=fixture.render(0);
        expect(partial[((fixture.height/2)*fixture.width+fixture.width/2+44)*4]==0,"foreground stripe cuts an arm outside emitter footprint");
        fixture.clear();fill(fixture.surface,fixture.depth(8),0,0,0);fill(fixture.transmission,.25f,.5f,.75f,1);
        var completed=fixture.render(0);var background=fixture.shade(1);var foregroundLight=fixture.shade(2);int center=((fixture.height/2)*fixture.width+fixture.width/2)*4;
        for(int channel=0;channel<3;channel++) {
            float transmission=(channel+1)*.25f;
            expect(Math.abs(completed[center+channel]-original[center+channel]*transmission)<.0001,"single colored pane transmits once channel="+channel);
            expect(Math.abs(completed[center+channel]-(foregroundLight[center+channel]+background[center+channel]))<.0001,"pre-refraction background plus foreground matches single transmission channel="+channel);
        }
        expect(maximum(foregroundLight)==0,"behind-pane glare is absent from late foreground layer");
        fixture.clear();rectangle(fixture.surface,fixture.width/2-5,fixture.height/2-5,10,10,fixture.depth(8));fill(fixture.transmission,.25f,.5f,.75f,1);
        var smallPane=fixture.render(0);int arm=((fixture.height/2)*fixture.width+fixture.width/2+32)*4;
        expect(Math.abs(smallPane[arm]-original[arm]*.25)<.0001,"emitter-only pane colors outgoing arms once");
        fixture.clear();rectangle(fixture.surface,fixture.width/2+28,0,12,fixture.height,fixture.depth(8));fill(fixture.transmission,.25f,.5f,.75f,1);
        var armPane=fixture.render(0);expect(Math.abs(armPane[arm]-original[arm]*.25)<.0001,"destination-only pane transmits arm once");
        fixture.clear();fill(fixture.surface,fixture.depth(100),0,0,0);fill(fixture.transmission,.25f,.5f,.75f,1);
        expect(difference(fixture.render(0),original)<.00001,"surface behind source does not tint glare");
        expect(maximum(fixture.shade(1))==0 && difference(fixture.shade(2),original)<.00001,"source before transparent surface belongs only to foreground layer");
        fixture.clear();fixture.view.rotationY(.54f);var edgeImage=fixture.render(0);finite(edgeImage,"near screen edge finite and correctly bounded");
        fill(fixture.result,0,0,0,1);fixture.compose.use();fixture.uniforms(fixture.compose);fixture.compose.integer("LayerMode",0);fixture.glare.bind(fixture.compose);
        glEnable(GL_SCISSOR_TEST);glScissor(fixture.glare.left,fixture.glare.bottom,fixture.glare.right-fixture.glare.left,fixture.glare.top-fixture.glare.bottom);draw();glDisable(GL_SCISSOR_TEST);
        expect(difference(edgeImage,pixels(fixture.result))==0,"projected glare rectangle preserves the full finite star at screen edge");
        fixture.view.rotationY(1.4f);expect(maximum(fixture.render(0))==0,"offscreen source has no edge-clamped star");
        fixture.view.rotationY((float)Math.PI);expect(maximum(fixture.render(0))==0,"behind-camera source has no star");
        fixture.view.identity();fixture.distance=0;expect(maximum(fixture.render(0))==0,"camera at exact singularity center has no new glare");fixture.distance=64;
    }
    private static void timeline(Fixture fixture)throws Exception {
        fixture.clear();var normal=fixture.render(0);int draws=fixture.sourceDraws;
        fixture.glow=0;expect(maximum(fixture.render(0))==0 && fixture.sourceDraws==draws,"glow zero skips source pass");fixture.glow=.7f;
        fixture.amount=0;expect(maximum(fixture.render(0))==0 && fixture.sourceDraws==draws,"ordinary black hole skips new effect");fixture.amount=1;
        fixture.brightness=0;expect(maximum(fixture.render(0))==0 && fixture.sourceDraws==draws,"zero brightness skips source pass");fixture.brightness=3.2f;
        fixture.phase=6;fixture.progress=.625f;expect(maximum(fixture.render(0))==0 && fixture.sourceDraws==draws,"pure-black phase skips source pass");
        fixture.phase=7;fixture.progress=.5f;expect(maximum(fixture.render(0))==0 && fixture.sourceDraws==draws,"return phase releases new effect");
        fixture.phase=4;fixture.progress=1;var contracted=fixture.render(0);
        expect(Math.abs(maximum(contracted)/maximum(normal)-.025)<.00001,"contraction follows emission but not a new distance threshold");
        fixture.phase=1;fixture.progress=.5f;expect(difference(normal,fixture.render(0))==0,"paused fixed time remains identical");
        int originalTexture=fixture.glare.source.textures[0];fixture.render(0);expect(fixture.glare.source.textures[0]==originalTexture,"source target reused without frame allocation");
        int samplerA=glGenSamplers(),samplerB=glGenSamplers();glActiveTexture(GL_TEXTURE11);glBindTexture(GL_TEXTURE_2D,fixture.transmission.textures[0]);glBindSampler(11,samplerA);glActiveTexture(GL_TEXTURE12);glBindTexture(GL_TEXTURE_2D,fixture.opaque.textures[0]);glBindSampler(12,samplerB);
        fixture.result.bind();glViewport(7,9,123,145);glEnable(GL_BLEND);glEnable(GL_DEPTH_TEST);glDepthMask(true);glActiveTexture(GL_TEXTURE4);
        int drawBuffer=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        try(var state=GlState.capture()){fixture.render(0);}
        int[] viewport=new int[4];glGetIntegerv(GL_VIEWPORT,viewport);
        expect(Arrays.equals(viewport,new int[]{7,9,123,145}) && glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)==drawBuffer,"framebuffer and viewport restored");
        expect(glIsEnabled(GL_BLEND) && glIsEnabled(GL_DEPTH_TEST) && glGetBoolean(GL_DEPTH_WRITEMASK) && glGetInteger(GL_ACTIVE_TEXTURE)==GL_TEXTURE4,"blend, depth and active unit restored");
        glActiveTexture(GL_TEXTURE11);expect(glGetInteger(GL_TEXTURE_BINDING_2D)==fixture.transmission.textures[0] && glGetIntegeri(GL_SAMPLER_BINDING,11)==samplerA,"new texture unit 11 restored");
        glActiveTexture(GL_TEXTURE12);expect(glGetInteger(GL_TEXTURE_BINDING_2D)==fixture.opaque.textures[0] && glGetIntegeri(GL_SAMPLER_BINDING,12)==samplerB,"new texture unit 12 restored");
        glBindSampler(11,0);glBindSampler(12,0);glDeleteSamplers(samplerA);glDeleteSamplers(samplerB);GlState.fullscreen();
        fixture.glare.close();expect(!glIsTexture(originalTexture) && fixture.glare.source.framebuffer==0,"source target released on reload");
        glUseProgram(0);
        try(var reloaded=new GlSingularityGlare(read("fullscreen.vsh"),read("singularity_source.fsh"))) {
            expect(reloaded.project(fixture.matrix,fixture.eye,0,0,-fixture.distance,fixture.radius,1,fixture.glow,fixture.brightness,1,.5,fixture.width,fixture.height),"reload accepts current camera without stale visibility");
            var sourceProgram=reloaded.begin();fixture.uniforms(sourceProgram);draw();
            expect(Math.abs(attachment(reloaded.source,0)[0]-1)<.00001 && attachment(reloaded.source,1)[3]==0,"recreated source target contains fresh front/back visibility");
            glUseProgram(0);glBindFramebuffer(GL_FRAMEBUFFER,0);
        }
    }
    private static void compositions(Path before)throws Exception {
        try(var now=new FinaleFlashGpuVerification.Pipeline();var old=new FinaleFlashGpuVerification.Pipeline(before,null)) {
            now.nasaEnabled=false;old.nasaEnabled=false; // Isolate glare changes from the new sky source; use a time between waves.
            now.resize(48,.5f);old.resize(48,.5f);
            var sheet=new BufferedImage(7*480,2*270,BufferedImage.TYPE_INT_RGB);var graphics=sheet.createGraphics();graphics.setFont(new Font(Font.SANS_SERIF,Font.BOLD,16));
            int column=0;for(int distance:new int[]{8,16,24,32,48,64,128}) {
                for(int row=0;row<2;row++) {
                    var pipeline=row==0?old:now;pipeline.cosmos(390,new Matrix4f(),0,-distance,false);String label=(row==0?"before-":"after-")+distance;
                    image(pipeline.cosmos,label);var frame=ImageIO.read(output.resolve(label+".png").toFile());graphics.drawImage(frame,column*480,row*270,480,270,null);graphics.setColor(Color.WHITE);graphics.drawString(label+" blocks",column*480+12,row*270+22);
                }
                if(distance<=16)expect(difference(pixels(now.cosmos),pixels(old.cosmos))==0,"complete image unchanged at near distance "+distance);
                column++;
            }
            graphics.dispose();ImageIO.write(sheet,"png",output.resolve("distance-comparison.png").toFile());
            for(int steps:new int[]{24,48,96}) {now.resize(steps,steps==24?.375f:steps==48?.5f:.75f);now.cosmos(390,new Matrix4f(),0,-64,false);finite(pixels(now.cosmos),"full composition finite at "+steps+" steps");}
            now.resize(48,.5f);
            for(int distance:new int[]{8,32,64,128}) {
                double[] oldTimes=new double[3],newTimes=new double[3];for(int trial=0;trial<3;trial++){oldTimes[trial]=measure(old,distance);newTimes[trial]=measure(now,distance);}Arrays.sort(oldTimes);Arrays.sort(newTimes);
                REPORT.append(String.format(Locale.ROOT,"GPU distance=%d before=%.3f after=%.3f delta=%+.3f ms (median 3 x 24 frames)%n",distance,oldTimes[1],newTimes[1],newTimes[1]-oldTimes[1]));
            }
        }
    }
    private static double measure(FinaleFlashGpuVerification.Pipeline pipeline,int distance) {
        var camera=new Matrix4f();for(int frame=0;frame<6;frame++)pipeline.cosmos(390,camera,0,-distance,false);
        int query=glGenQueries();glBeginQuery(GL_TIME_ELAPSED,query);for(int frame=0;frame<24;frame++)pipeline.cosmos(390,camera,0,-distance,false);glEndQuery(GL_TIME_ELAPSED);
        double milliseconds=glGetQueryObjectui64(query,GL_QUERY_RESULT)/24e6;glDeleteQueries(query);return milliseconds;
    }
    public static void main(String[] args)throws Exception {
        source=Path.of(args[0]);output=Path.of(args[1]);Path before=Path.of(args[2]);Files.createDirectories(output);
        expect(glfwInit(),"GLFW");glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
        long window=glfwCreateWindow(64,64,"singularity distant glare",0,0);expect(window!=0,"hidden context");glfwMakeContextCurrent(window);GL.createCapabilities();vao=glGenVertexArrays();GlState.fullscreen();
        REPORT.append("GPU ").append(glGetString(GL_RENDERER)).append(". Synthetic production star/subject/bloom composition, 1920x1080 balanced; excludes Minecraft and host effects.\n");
        try {
            for(String signature:new String[]{"vec3 environmentLens(","void finaleMembranes("}) expect(VerificationShaders.function(source,signature).equals(VerificationShaders.function(before,signature)),"existing lens and membranes unchanged: "+signature);
            // Wave propagation now changes intentionally; verifyShockwaveGpu covers its lens/occlusion contracts.
            try(var fixture=new Fixture()){distances(fixture);errorFree("distance fixtures");visibility(fixture);errorFree("visibility fixtures");timeline(fixture);errorFree("lifecycle fixtures");glUseProgram(0);glBindFramebuffer(GL_FRAMEBUFFER,0);}
            errorFree("fixture cleanup");compositions(before);errorFree("production composition");
        }finally{Files.writeString(output.resolve("report.txt"),REPORT.toString());glDeleteVertexArrays(vao);glfwDestroyWindow(window);glfwTerminate();}
        System.out.println(REPORT+"Checks: "+checks);
    }
}

