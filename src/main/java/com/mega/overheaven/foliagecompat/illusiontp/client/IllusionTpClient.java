package com.mega.overheaven.foliagecompat.illusiontp.client;

import com.mega.overheaven.foliagecompat.FoliageCompat;
import com.mega.overheaven.foliagecompat.PatchLog;
import com.mega.overheaven.foliagecompat.blackhole.client.IllusionTpRenderer;
import com.mega.overheaven.foliagecompat.illusiontp.IllusionTpMovement;
import com.mega.overheaven.foliagecompat.illusiontp.IllusionTpNetwork;
import com.mega.overheaven.foliagecompat.illusiontp.IllusionTpPlayback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = FoliageCompat.MOD_ID, value = Dist.CLIENT)
public final class IllusionTpClient {
    private static IllusionTpNetwork.Update state;
    private static IllusionTpPlayback playback;
    private static int remaining;
    private static boolean changingDimension;
    private IllusionTpClient() {}

    public static void accept(IllusionTpNetwork.Update update) {
        if (update.action() == IllusionTpNetwork.BEGIN) {
            clear();
            state = update;
            playback = new IllusionTpPlayback();
            remaining = 1300;
            lockPosition();
        } else if (state != null && state.run().equals(update.run())) {
            if (update.action() == IllusionTpNetwork.CANCEL) clear();
            else if (playback.phase() == IllusionTpPlayback.Phase.WAITING) {
                state = update;
                playback.arrived();
                remaining = 1300;
                lockPosition();
            }
        }
    }
    public static boolean active() { return playback != null; }
    public static boolean revealing() { return active() && playback.phase() == IllusionTpPlayback.Phase.REVEALING; }
    public static boolean opaque() { return active() && playback.opaque(); }
    public static double progress(float partial) {
        return active() ? playback.progress(Minecraft.getInstance().isPaused() ? 0 : partial) : 0;
    }
    public static double radius(float partial) {
        return active() ? playback.radius(Minecraft.getInstance().isPaused() ? 0 : partial) : 0;
    }
    public static double fadedRadius(float partial) {
        return active() ? playback.fadedRadius(Minecraft.getInstance().isPaused() ? 0 : partial) : 0;
    }
    public static double settledRadius(float partial) {
        return active() ? playback.settledRadius(Minecraft.getInstance().isPaused() ? 0 : partial) : 0;
    }
    public static boolean destinationReady() {
        var mc = Minecraft.getInstance();
        if (state == null || state.action() != IllusionTpNetwork.ARRIVED || mc.level == null || mc.player == null
                || !mc.level.dimension().location().equals(state.dimension())
                || mc.player.position().distanceToSqr(state.anchor()) >= 0.01
                || mc.screen instanceof ReceivingLevelScreen || mc.screen instanceof ProgressScreen) return false;
        var position = BlockPos.containing(state.anchor());
        // With the loading screens removed, retain their terrain readiness requirement here.
        return mc.level.hasChunkAt(position) && (mc.level.isOutsideBuildHeight(position.getY())
                || mc.levelRenderer.isChunkCompiled(position));
    }
    public static void rendered(boolean complete, boolean destinationFrame, boolean solidFrame) {
        if (!active()) return;
        var signal = playback.rendered(complete, destinationFrame && destinationReady(), solidFrame);
        if (signal == IllusionTpPlayback.Signal.COVERED) IllusionTpNetwork.reply(state.run(), IllusionTpNetwork.COVERED);
        else if (signal == IllusionTpPlayback.Signal.FINISHED) {
            IllusionTpNetwork.reply(state.run(), IllusionTpNetwork.FINISHED);
            clear();
        }
    }
    private static void lockPosition() {
        var mc = Minecraft.getInstance();
        if (state == null || mc.player == null || mc.level == null || !mc.level.dimension().location().equals(state.dimension())) return;
        var anchor = state.anchor();
        mc.player.setPos(anchor.x, anchor.y, anchor.z);
        mc.player.xo = anchor.x; mc.player.yo = anchor.y; mc.player.zo = anchor.z;
        mc.player.setDeltaMovement(Vec3.ZERO);
        mc.player.fallDistance = 0;
        mc.player.setSprinting(false);
    }
    public static void fail(RuntimeException error) {
        PatchLog.LOGGER.error("illusionTp rendering failed; releasing the transition and movement lock", error);
        abort();
    }
    public static void abort() {
        if (state != null && Minecraft.getInstance().getConnection() != null)
            IllusionTpNetwork.reply(state.run(), IllusionTpNetwork.ABORT);
        clear();
    }
    public static void clear() {
        state = null; playback = null; remaining = 0; changingDimension = false;
        IllusionTpRenderer.INSTANCE.close();
    }
    @SubscribeEvent public static void input(MovementInputUpdateEvent event) {
        if (!active() || event.getEntity() != Minecraft.getInstance().player) return;
        var input = event.getInput();
        input.forwardImpulse = 0; input.leftImpulse = 0;
        input.up = false; input.down = false; input.left = false; input.right = false;
        input.jumping = false; input.shiftKeyDown = false;
    }
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (active() && event.player == Minecraft.getInstance().player) lockPosition();
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active()) return;
        var mc = Minecraft.getInstance();
        if (mc.getConnection() == null) { clear(); return; }
        if (mc.player != null && !mc.player.isAlive()) { abort(); return; }
        if (mc.isPaused()) return;
        if (--remaining <= 0) { abort(); return; }
        playback.tick();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (!active() || event.getLevel() != Minecraft.getInstance().level) return;
        if (!opaque()) { abort(); return; }
        changingDimension = true;
        // Keep the handshake and solid cover, but never carry old-world depth or GPU queries into the new level.
        IllusionTpRenderer.INSTANCE.close();
    }
    @SubscribeEvent public static void loadingScreen(ScreenEvent.Opening event) {
        if (opaque() && changingDimension && (event.getNewScreen() instanceof ReceivingLevelScreen
                || event.getNewScreen() instanceof ProgressScreen)) {
            // Null closes any previous screen too; cancelling would leave chat/pause open and release the mouse.
            event.setNewScreen(null);
        }
    }

    @Mod.EventBusSubscriber(modid = FoliageCompat.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                IllusionTpNetwork.clientReceiver(IllusionTpClient::accept);
                IllusionTpMovement.clientLock(entity -> active() && entity == Minecraft.getInstance().player);
            });
        }
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> Minecraft.getInstance().execute(IllusionTpClient::abort));
        }
    }
}
