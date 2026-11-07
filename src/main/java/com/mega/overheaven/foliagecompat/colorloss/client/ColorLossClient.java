package com.mega.overheaven.foliagecompat.colorloss.client;

import com.mega.overheaven.foliagecompat.FoliageCompat;
import com.mega.overheaven.foliagecompat.PatchLog;
import com.mega.overheaven.foliagecompat.blackhole.client.ColorLossRenderer;
import com.mega.overheaven.foliagecompat.colorloss.ColorLossNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = FoliageCompat.MOD_ID, value = Dist.CLIENT)
public final class ColorLossClient {
    private static final ColorLossPlayback playback = new ColorLossPlayback();
    private static boolean failed;
    private ColorLossClient() {}
    public static void accept(ColorLossNetwork.Snapshot snapshot) { playback.accept(snapshot); }
    public static boolean sample(float partial, float[] rgb) {
        if (failed) { java.util.Arrays.fill(rgb, 0); return false; }
        return playback.sample(partial, Minecraft.getInstance().isPaused(), rgb);
    }
    public static boolean consumeHistoryReset() { return playback.consumeHistoryReset(); }
    public static void fail(RuntimeException error) {
        failed = true;
        playback.invalidateHistory();
        PatchLog.LOGGER.error("Color loss rendering failed; disabled until resource reload", error);
        ColorLossRenderer.INSTANCE.close();
    }
    public static void releaseResources() { ColorLossRenderer.INSTANCE.close(); failed = false; playback.invalidateHistory(); }
    public static void clear() { playback.clear(); releaseResources(); }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !Minecraft.getInstance().isPaused()
                && Minecraft.getInstance().level != null) playback.tick();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) Minecraft.getInstance().execute(ColorLossClient::releaseResources);
    }
    @Mod.EventBusSubscriber(modid = FoliageCompat.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> ColorLossNetwork.receiver(ColorLossClient::accept));
        }
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> Minecraft.getInstance().execute(ColorLossClient::releaseResources));
        }
    }
}
