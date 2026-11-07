package com.mega.overheaven.foliagecompat.worldwarp.client;

import com.mega.overheaven.foliagecompat.FoliageCompat;
import com.mega.overheaven.foliagecompat.worldwarp.WorldWarpMath;
import com.mega.overheaven.foliagecompat.worldwarp.WorldWarpMixinPlugin;
import com.mega.overheaven.foliagecompat.worldwarp.WorldWarpNetwork;
import com.mega.overheaven.foliagecompat.worldwarp.WorldWarpState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@Mod.EventBusSubscriber(modid = FoliageCompat.MOD_ID, value = Dist.CLIENT)
public final class WorldWarpClient {
    private static WorldWarpState state;
    private static long tick, epoch;
    private static boolean discontinuity = true, warned, worldPass;
    private static Object pack;
    private static volatile WorldWarpFrame current = WorldWarpFrame.OFF;
    private static WorldWarpFrame previous = WorldWarpFrame.OFF;
    private static boolean resetHistory;
    private static boolean runtimeSupported;
    private static final Matrix4f inverseView = new Matrix4f();
    private WorldWarpClient() {}
    public static WorldWarpFrame frame() { return current; }
    public static WorldWarpFrame previous() { return previous; }
    public static boolean resetHistory() { return resetHistory; }
    public static boolean worldPass() {
        return worldPass && current.enabled() && !OculusWarpBridge.shadows();
    }
    public static void accept(WorldWarpNetwork.Update update) {
        boolean stop = state != null && update.state() != null && state.startTick() == update.state().startTick()
                && update.state().fadeStart() >= 0;
        state = update.state(); tick = update.tick(); warned = false;
        if (!stop) { discontinuity = true; epoch++; }
    }
    public static void begin(Camera camera, float partial, Matrix4f view) {
        var mc = Minecraft.getInstance();
        previous = current;
        Object nextPack = runtimeSupported ? OculusWarpBridge.pack() : null;
        if (pack != nextPack) { pack = nextPack; discontinuity = true; epoch++; }
        if (state != null && (mc.level == null || !mc.level.dimension().location().equals(state.dimension()))) {
            state = null; discontinuity = true; epoch++;
        }
        // Promote before adding: long + float would discard sub-tick precision in older worlds.
        double time = (double) tick + partial;
        if (state != null && state.finished(time)) { state = null; discontinuity = true; epoch++; }
        boolean enabled = state != null && state.strength() > 0 && nextPack != null && mc.player != null;
        if (state != null && nextPack == null && !warned && mc.player != null) {
            mc.player.displayClientMessage(Component.literal("worldWarp 需要 Oculus 1.8.0、Embeddium 0.3.31 和已适配的 Complementary Unbound r5.9.3。"), false);
            warned = true;
        }
        Vec3 c = camera.getPosition();
        current = enabled ? WorldWarpFrame.sample(state, c, mc.player.getEyePosition(partial), time, epoch)
                : new WorldWarpFrame(c, Vec3.ZERO, new Vec3(0, 0, 1), 0, 0, false, epoch);
        resetHistory = discontinuity || previous.enabled() != enabled;
        discontinuity = false;
        inverseView.set(view).invert();
        worldPass = true;
    }
    public static VertexSorting sorting(VertexSorting original) {
        if (!worldPass() || OculusWarpBridge.hand()) return original;
        var frame = current;
        var localToWorld = new Matrix4f(inverseView).mul(RenderSystem.getModelViewMatrix());
        // Entity buffers already contain their PoseStack transform. Undo only the captured camera view.
        return VertexSorting.byDistance(point -> {
            Vector3f p = localToWorld.transformPosition(point, new Vector3f());
            return (float) WorldWarpMath.warp(new Vec3(p.x, p.y, p.z), frame.center(), frame.axis(), frame.slope(), frame.distanceOffset()).lengthSqr();
        });
    }
    public static void end() { worldPass = false; }
    public static AABB bounds(AABB box, boolean relative) {
        if (!worldPass() || !Double.isFinite(box.getSize())) return box;
        var f = current;
        return WorldWarpMath.sweptBounds(box, relative ? f.center() : f.center().add(f.camera()), f.axis());
    }
    public static void clear() {
        state = null; discontinuity = true; warned = false; worldPass = false; epoch++;
        current = WorldWarpFrame.OFF; previous = WorldWarpFrame.OFF;
    }
    public static void reload() { discontinuity = true; warned = false; pack = null; epoch++; }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && Minecraft.getInstance().level != null && !Minecraft.getInstance().isPaused()) tick++;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) clear();
    }
    @Mod.EventBusSubscriber(modid = FoliageCompat.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                runtimeSupported = WorldWarpMixinPlugin.supportedRuntime();
                WorldWarpNetwork.receiver(WorldWarpClient::accept);
            });
        }
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> Minecraft.getInstance().execute(WorldWarpClient::reload));
        }
    }
}
