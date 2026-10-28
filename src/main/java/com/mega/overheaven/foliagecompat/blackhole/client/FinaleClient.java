package com.mega.overheaven.foliagecompat.blackhole.client;

import com.mega.overheaven.foliagecompat.FoliageCompat;
import com.mega.overheaven.foliagecompat.finale.*;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=FoliageCompat.MOD_ID,value=Dist.CLIENT)
public final class FinaleClient {
    private static FinaleNetwork.Update state;
    private static int localTicks;
    private static final FinaleWaveFeedback feedback=new FinaleWaveFeedback();
    private static boolean feedbackDiscontinuity;
    private static net.minecraft.client.CameraType lastCameraType;
    private static net.minecraft.world.entity.Entity lastCameraEntity;
    private static long[] glass=new long[0],debris=new long[0];
    private static boolean failed;
    private static java.util.UUID failedRun;
    public static void accept(FinaleNetwork.Update update) {
        if(update.hole()==null) { clear(); return; }
        if(update.run().equals(failedRun)) return;
        boolean fresh=state==null || !state.run().equals(update.run());
        if(fresh) { clear(); failed=false; }
        if(!fresh && Math.abs(update.elapsed()-ticks(0))>2) feedbackDiscontinuity=true;
        state=update; localTicks=0;
        if(fresh || update.glass().length>0 || update.debris().length>0) { glass=update.glass().clone(); debris=update.debris().clone(); FinaleRenderer.INSTANCE.geometryChanged(); }
        if(fresh) { FinaleAudio.resetCursor(ticks(0)); FinaleRenderer.INSTANCE.prepareSky(); }
    }
    public static FinaleNetwork.Update state() { return state; }
    public static boolean active() {
        var level=Minecraft.getInstance().level;
        return !failed && state!=null && !state.preparing() && level!=null && level.dimension().location().toString().equals(state.dimension()) && localTicks<160;
    }
    public static double ticks(float partial) { return state==null?0:Math.min(state.timeline().total(),state.elapsed()+(state.paused() || state.preparing() || state.stopping()>=0?0:Math.min(45,localTicks+partial))); }
    public static FinaleTimeline.Frame frame(float partial) { return state==null?new FinaleTimeline.Frame(FinaleTimeline.Phase.RETURN,1):state.timeline().frame(ticks(partial)); }
    public static float stopping(float partial) { return state==null || state.stopping()<0?-1:Math.min(1,(state.stopping()+localTicks+partial)/40f); }
    public static FinaleTimeline.Exposure exposure(float partial) {
        return state==null?new FinaleTimeline.Exposure(0,0,0):state.timeline().exposure(ticks(partial),stopping(partial));
    }
    public static double coverage(float partial) { return exposure(partial).coverage(); }
    public static double flash(float partial) { return exposure(partial).white(); }
    public static double pulse(float partial) { return exposure(partial).pulse(); }
    public static float gameGain() {
        if(!active()) return 1;
        float stop=stopping(0); return stop>=0?(float)FinaleTimeline.smooth(0,1,stop):(float)FinaleTimeline.gameGain(frame(0));
    }
    public static boolean hideHud() { return active() && (stopping(0)>=0?stopping(0)<0.9:frame(0).phase()!=FinaleTimeline.Phase.RETURN || frame(0).progress()<0.9); }
    public static boolean source(String id) { return active() && state.hole().id().equals(id); }
    public static boolean skipWorld(float partial) { return active() && exposure(partial).coverage()>=1; }
    public static long[] glass() { return glass; }
    public static long[] debris() { return debris; }
    public static void fail(Throwable error) { if(state!=null) failedRun=state.run(); com.mega.overheaven.foliagecompat.PatchLog.LOGGER.error("Finale renderer disabled for this session; input, HUD and sound restored",error); clear(); failed=true; }
    public static FinaleWaveFeedback feedback() { return feedback; }
    public static void resetFeedback() { feedback.reset(); feedbackDiscontinuity=true;lastCameraType=null;lastCameraEntity=null; }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles event) {
        if(!active() || OptionalShaders.shadow()) return;
        FinaleRenderer.INSTANCE.warmSky();
        float partial=(float)event.getPartialTick();
        if(stopping(partial)>=0 || FinaleTimeline.black(frame(partial))) { resetFeedback(); return; }
        var type=Minecraft.getInstance().options.getCameraType();var entity=event.getCamera().getEntity();
        if(type!=lastCameraType || entity!=lastCameraEntity) resetFeedback();lastCameraType=type;lastCameraEntity=entity;
        var position=event.getCamera().getPosition();var hole=state.hole();
        double dx=position.x-hole.x(),dy=position.y-hole.y(),dz=position.z-hole.z();
        feedback.update(state.timeline(),ticks(partial),hole.value("radius"),hole.value("outer"),hole.value("thickness"),
            position.x,position.y,position.z,Math.sqrt(dx*dx+dy*dy+dz*dz),state.paused() || Minecraft.getInstance().isPaused(),feedbackDiscontinuity);
        feedbackDiscontinuity=false;
        event.setPitch(event.getPitch()+(float)feedback.pitch);event.setYaw(event.getYaw()+(float)feedback.yaw);event.setRoll(event.getRoll()+(float)feedback.roll);
    }
    public static void clear() { resetFeedback(); state=null; localTicks=0; glass=new long[0]; debris=new long[0]; FinaleAudio.close(); FinaleRenderer.INSTANCE.close(); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END) return;
        var mc=Minecraft.getInstance(); if(state==null) return;
        if(mc.level==null || !mc.level.dimension().location().toString().equals(state.dimension())) { clear(); return; }
        if(!mc.isPaused()) { localTicks++; FinaleAudio.tick(); }
        if(localTicks>=160) clear();
    }
    @SubscribeEvent public static void hand(RenderHandEvent event) { if(hideHud()) event.setCanceled(true); }
    @SubscribeEvent public static void overlay(RenderGuiOverlayEvent.Pre event) { if(hideHud() && !event.getOverlay().id().equals(VanillaGuiOverlay.CHAT_PANEL.id())) event.setCanceled(true); }
}
