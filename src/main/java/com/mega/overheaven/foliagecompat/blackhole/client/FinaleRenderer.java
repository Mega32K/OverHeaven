package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.opengl.GL33C.*;
import com.mega.overheaven.foliagecompat.finale.FinaleTimeline;
import com.mega.overheaven.foliagecompat.finale.FinaleChoreography;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Late display-space takeover. The scene has already passed through host world postprocessing. */
public final class FinaleRenderer implements AutoCloseable {
    public static final FinaleRenderer INSTANCE=new FinaleRenderer();
    private final Matrix4f vp=new Matrix4f(),inverse=new Matrix4f();
    private final Vector4f eye=new Vector4f();
    private final GlTarget scene=new GlTarget(GL_RGBA16F),stars=new GlTarget(GL_RGBA16F).linear(),cosmicScene=new GlTarget(GL_RGBA16F);
    private final BlackHoleRenderer subjects=new BlackHoleRenderer(true);
    private final FinaleGeometry geometry=new FinaleGeometry();
    private final GpuDiagnostics timing=new GpuDiagnostics();
    private final FinaleStarMap sky=new FinaleStarMap(name -> Minecraft.getInstance().getResourceManager().open(new net.minecraft.resources.ResourceLocation(com.mega.overheaven.foliagecompat.FoliageCompat.MOD_ID,"textures/finale/"+name)));
    private GlProgram compose;
    private static final com.mega.overheaven.foliagecompat.finale.FinaleWaves.State WAVE=new com.mega.overheaven.foliagecompat.finale.FinaleWaves.State();
    private static final float[] BLACK={0,0,0,1};
    private int vao;
    private double cameraX,cameraY,cameraZ;
    private float projectionScale;
    private boolean cameraValid;
    public void camera(Matrix4f view,Matrix4f projection,Camera camera) {
        if(OptionalShaders.shadow()) return;
        vp.set(projection).mul(view); inverse.set(vp).invert(); eye.set(0,0,1,0); inverse.transform(eye); eye.div(eye.w);
        projectionScale=Math.max(0.001f,Math.abs(projection.m11()));
        var position=camera.getPosition(); cameraX=position.x; cameraY=position.y; cameraZ=position.z; cameraValid=Float.isFinite(eye.x);
    }
    public void geometryChanged() { geometry.dirty(); }
    public void prepareSky() { sky.prepare(); }
    public void warmSky() { sky.warm(); }
    void nearGeometry(int opaque,int surface,int width,int height,float partial) {
        if(!FinaleClient.active() || !cameraValid) return;
        geometry.render(vp,inverse,eye,cameraX,cameraY,cameraZ,opaque,surface,width,height,partial);
    }
    static double nearAmount(String id,float partial) {
        if(!FinaleClient.source(id)) return 0;
        var frame=FinaleClient.frame(partial); double amount=1;
        if(frame.phase()==FinaleTimeline.Phase.EXPOSE) amount=com.mega.overheaven.foliagecompat.finale.CausalTransition.reveal(frame.progress());
        if(frame.phase()==FinaleTimeline.Phase.RETURN) amount=1-FinaleTimeline.smooth(0,1,frame.progress());
        if(FinaleClient.stopping(partial)>=0) amount*=1-FinaleTimeline.smooth(0,1,FinaleClient.stopping(partial));
        return amount;
    }
    static void nearUniforms(GlProgram program,String id,float partial) {
        CausalUniforms.subject(program,id,partial);
        if(!FinaleClient.source(id)) { program.scalar("FinaleAmount",0); program.scalar("FinalePulse",0); program.integer("WavePresent",0); program.scalar("WaveSupport",0); program.scalar("GlarePulse",0); return; }
        var frame=FinaleClient.frame(partial);
        var hole=FinaleClient.state().hole();
        com.mega.overheaven.foliagecompat.finale.FinaleWaves.evaluate(FinaleClient.state().timeline(),FinaleClient.ticks(partial),hole.value("radius"),hole.value("outer"),hole.value("thickness"),WAVE);
        FinaleWaveUniforms.apply(program,WAVE);
        program.scalar("GlarePulse",com.mega.overheaven.foliagecompat.finale.FinaleWaves.flare(FinaleClient.state().timeline(),FinaleClient.ticks(partial)));
        program.scalar("FinaleAmount",nearAmount(id,partial)); program.integer("FinalePhase",frame.phase().ordinal()); program.scalar("FinaleProgress",frame.progress()); program.scalar("FinaleTime",FinaleClient.ticks(partial)/20);
        program.scalar("FinalePulse",FinaleClient.pulse(partial));
        program.scalar("FinaleOrbit",FinaleChoreography.motion(FinaleClient.state().timeline(),FinaleClient.ticks(partial)).angle());
    }
    public void render(float partial) {
        if(!FinaleClient.active() || !cameraValid || FinaleClient.coverage(partial)<=0 && FinaleClient.flash(partial)<=0 && FinaleClient.feedback().exposure<=0 && FinaleClient.feedback().chroma<=0 && CausalUniforms.exposure(partial)<=0) return;
        var mc=Minecraft.getInstance(); var target=mc.getMainRenderTarget();
        try(var state=GlState.capture()) {
            if(compose==null) { compose=new GlProgram("finale_composite"); vao=glGenVertexArrays(); }
            int width=target.width,height=target.height; scene.resize(width,height);
            timing.frame(BlackHoleClient.diagnostics,true,"finale/"+sky.path()+"/"+FinaleClient.frame(partial).phase().id(),width,height); timing.start(0);
            GlState.fullscreen(); glBindFramebuffer(GL_READ_FRAMEBUFFER,target.frameBufferId); glBindFramebuffer(GL_DRAW_FRAMEBUFFER,scene.framebuffer);
            glBlitFramebuffer(0,0,width,height,0,0,width,height,GL_COLOR_BUFFER_BIT,GL_NEAREST);
            glBindVertexArray(vao);
            if(FinaleClient.coverage(partial)>0) {
                stars.resize(Math.max(1,(int)(width*BlackHoleClient.resolution)),Math.max(1,(int)(height*BlackHoleClient.resolution)));
                cosmicScene.resize(width,height); stars.bind();
                boolean nasa=sky.drawBackground(p->uniforms(p,partial),vao);
                glBindFramebuffer(GL_READ_FRAMEBUFFER,stars.framebuffer); glBindFramebuffer(GL_DRAW_FRAMEBUFFER,cosmicScene.framebuffer);
                glBlitFramebuffer(0,0,stars.width,stars.height,0,0,width,height,GL_COLOR_BUFFER_BIT,GL_LINEAR);
                if(nasa) { cosmicScene.bind(); sky.drawStars(p->uniforms(p,partial),width,height,BlackHoleClient.steps==24?0:BlackHoleClient.steps==96?2:1); }
                timing.end(0);
                var frame=FinaleClient.frame(partial);
                if(!FinaleTimeline.black(frame) && frame.phase()!=FinaleTimeline.Phase.RETURN) subjects.renderCosmos(cosmicScene,vp,eye,cameraX,cameraY,cameraZ,2.0f/(height*projectionScale),partial);
            }
            timing.end(0);
            timing.start(1); glBindFramebuffer(GL_FRAMEBUFFER,target.frameBufferId); glViewport(0,0,width,height); compose.use(); uniforms(compose,partial);
            compose.texture("Scene",0,scene.textures[0]); compose.texture("Cosmos",1,FinaleClient.coverage(partial)>0?cosmicScene.textures[0]:scene.textures[0]);
            compose.pair("FullSize",width,height); compose.scalar("CameraExposure",Math.max(FinaleClient.feedback().exposure,CausalUniforms.exposure(partial))); compose.scalar("CameraChroma",FinaleClient.feedback().chroma);
            compose.scalar("Flash",FinaleClient.flash(partial)); compose.scalar("Pulse",FinaleClient.pulse(partial));
            glBindVertexArray(vao); glDrawArrays(GL_TRIANGLES,0,3); timing.end(1);
        } catch(RuntimeException error) { FinaleClient.fail(error); }
        finally { timing.end(0); timing.end(1); }
    }
    private void uniforms(GlProgram p,float partial) {
        var state=FinaleClient.state(); var frame=FinaleClient.frame(partial);
        p.matrix("InverseVP",inverse); p.matrix("VP",vp); p.vector("Eye",eye.x,eye.y,eye.z);
        p.vector("Center",(float)(state.hole().x()-cameraX),(float)(state.hole().y()-cameraY),(float)(state.hole().z()-cameraZ));
        var hole=state.hole(); double tilt=Math.toRadians(hole.value("tilt")),yaw=Math.toRadians(hole.value("yaw"));
        p.scalar("Radius",hole.value("radius"));p.scalar("Glow",hole.value("glow"));
        p.vector("DiskNormal",(float)(Math.sin(yaw)*Math.sin(tilt)),(float)Math.cos(tilt),(float)(Math.cos(yaw)*Math.sin(tilt)));
        p.vector("DiskRight",(float)Math.cos(yaw),0,(float)-Math.sin(yaw));
        p.vector("DiskForward",(float)(-Math.sin(yaw)*Math.cos(tilt)),(float)Math.sin(tilt),(float)(-Math.cos(yaw)*Math.cos(tilt)));
        p.scalar("Time",FinaleClient.ticks(partial)/20); p.scalar("Seed",(state.hole().seed()&65535)/257.0);
        var motion=FinaleChoreography.motion(state.timeline(),FinaleClient.ticks(partial));
        p.scalar("OrbitAngle",motion.angle()); p.scalar("OrbitSpeed",motion.speed()); p.integer("StarsOnly",0);
        p.integer("Phase",frame.phase().ordinal()); p.scalar("Progress",frame.progress()); p.scalar("Coverage",FinaleClient.coverage(partial));
        p.scalar("Extinction",frame.phase().ordinal()>5?1:frame.phase().ordinal()==5?frame.progress():0);
        p.integer("Black",FinaleClient.stopping(partial)<0 && FinaleTimeline.black(frame)?1:0); p.integer("Detail",BlackHoleClient.steps==24?0:BlackHoleClient.steps==96?2:1);
    }
    @Override public void close() {
        try(var state=GlState.capture()) { subjects.close(); cosmicScene.close(); scene.close(); stars.close(); geometry.close(); sky.close(); if(compose!=null) compose.close(); compose=null; if(vao!=0) glDeleteVertexArrays(vao); vao=0; timing.close(); }
        cameraValid=false;
    }
}
