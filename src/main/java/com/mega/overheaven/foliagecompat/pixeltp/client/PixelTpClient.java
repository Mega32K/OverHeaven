package com.mega.overheaven.foliagecompat.pixeltp.client;

import com.mega.overheaven.foliagecompat.FoliageCompat;
import com.mega.overheaven.foliagecompat.PatchLog;
import com.mega.overheaven.foliagecompat.blackhole.client.PixelTpRenderer;
import com.mega.overheaven.foliagecompat.pixeltp.PixelTpNetwork;
import com.mega.overheaven.foliagecompat.pixeltp.PixelTpPlayback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = FoliageCompat.MOD_ID, value = Dist.CLIENT)
public final class PixelTpClient {
    private static PixelTpNetwork.Update state;
    private static PixelTpPlayback playback;
    private static int remaining;
    private PixelTpClient() {}

    public static void accept(PixelTpNetwork.Update update) {
        if (update.action() == PixelTpNetwork.BEGIN) {
            clear();
            state = update;
            playback = new PixelTpPlayback(update.timing());
            remaining = update.timing().total() + 1200;
        } else if (state != null && state.run().equals(update.run())) {
            if (update.action() == PixelTpNetwork.CANCEL) clear();
            else {
                state = update;
                playback.arrived();
            }
        }
    }
    public static boolean active() { return playback != null; }
    public static double amount(float partial) {
        return playback == null ? 0 : playback.amount(Minecraft.getInstance().isPaused() ? 0 : partial);
    }
    private static boolean destinationReady() {
        var mc = Minecraft.getInstance();
        return state != null && state.action() == PixelTpNetwork.ARRIVED && mc.level != null && mc.player != null
                && mc.level.dimension().location().equals(state.dimension()) && mc.level.hasChunkAt(state.destination())
                && !(mc.screen instanceof ReceivingLevelScreen);
    }
    public static void rendered(double amount) {
        if (playback != null && playback.rendered(amount, destinationReady()))
            PixelTpNetwork.reply(state.run(), PixelTpNetwork.COVERED);
    }
    public static void fail(RuntimeException error) {
        PatchLog.LOGGER.error("pixelTp renderer failed; cancelling transition and releasing its GPU resources", error);
        abort();
    }
    private static void abort() {
        if (state != null && Minecraft.getInstance().getConnection() != null)
            PixelTpNetwork.reply(state.run(), PixelTpNetwork.ABORT);
        clear();
    }
    public static void clear() {
        state = null;
        playback = null;
        remaining = 0;
        PixelTpRenderer.INSTANCE.close();
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || playback == null) return;
        var mc = Minecraft.getInstance();
        if (mc.getConnection() == null) { clear(); return; }
        if (mc.player != null && !mc.player.isAlive()) { abort(); return; }
        if (mc.isPaused()) return;
        if (--remaining <= 0) { abort(); return; }
        playback.tick(destinationReady());
        if (playback.phase() == PixelTpPlayback.Phase.FINISHED) {
            PixelTpNetwork.reply(state.run(), PixelTpNetwork.FINISHED);
            clear();
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
    @SubscribeEvent public static void loadingScreen(ScreenEvent.Render.Pre event) {
        if (active() && amount(0) >= 1 && event.getScreen() instanceof ReceivingLevelScreen)
            event.setCanceled(true);
    }

    @Mod.EventBusSubscriber(modid = FoliageCompat.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> PixelTpNetwork.clientReceiver(PixelTpClient::accept));
        }
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager ->
                    Minecraft.getInstance().execute(() -> PixelTpRenderer.INSTANCE.close()));
        }
    }
}
