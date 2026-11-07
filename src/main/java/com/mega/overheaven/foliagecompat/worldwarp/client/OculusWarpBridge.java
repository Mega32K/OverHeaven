package com.mega.overheaven.foliagecompat.worldwarp.client;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.irisshaders.iris.pathways.HandRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Loaded only through the optional Oculus hooks / presence-gated physical client. */
public final class OculusWarpBridge {
    private OculusWarpBridge() {}
    public static Object pack() {
        Object p = Iris.getCurrentPack().orElse(null);
        return IrisApi.getInstance().isShaderPackInUse() && p instanceof WarpedShaderPack w && w.overheaven$worldWarp() ? p : null;
    }
    public static boolean shadows() { return ShadowRenderingState.areShadowsCurrentlyBeingRendered(); }
    public static boolean hand() { return HandRenderer.INSTANCE.isActive(); }
    private static Vector3f vec(Vec3 v) { return new Vector3f((float)v.x, (float)v.y, (float)v.z); }
    public static void uniforms(UniformHolder holder) {
        var frequency = UniformUpdateFrequency.PER_FRAME;
        holder.uniform3f(frequency, "ohWarpCenter", () -> vec(WorldWarpClient.frame().center()));
        holder.uniform3f(frequency, "ohWarpAxis", () -> vec(WorldWarpClient.frame().axis()));
        holder.uniform1f(frequency, "ohWarpSlope", () -> (float)WorldWarpClient.frame().slope());
        holder.uniform1f(frequency, "ohWarpDistanceOffset", () -> (float)WorldWarpClient.frame().distanceOffset());
        holder.uniform3f(frequency, "ohWarpPreviousCenter", () -> vec(WorldWarpClient.previous().center()));
        holder.uniform3f(frequency, "ohWarpPreviousAxis", () -> vec(WorldWarpClient.previous().axis()));
        holder.uniform1f(frequency, "ohWarpPreviousSlope", () -> (float)WorldWarpClient.previous().slope());
        holder.uniform1f(frequency, "ohWarpPreviousDistanceOffset", () -> (float)WorldWarpClient.previous().distanceOffset());
        holder.uniform3f(frequency, "ohWarpCameraDelta", () -> vec(WorldWarpClient.frame().camera().subtract(WorldWarpClient.previous().camera())));
        holder.uniform1b(frequency, "ohWarpResetHistory", WorldWarpClient::resetHistory);
    }
}
