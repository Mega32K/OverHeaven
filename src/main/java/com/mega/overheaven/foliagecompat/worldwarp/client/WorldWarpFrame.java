package com.mega.overheaven.foliagecompat.worldwarp.client;

import com.mega.overheaven.foliagecompat.worldwarp.WorldWarpState;
import net.minecraft.world.phys.Vec3;

/** Immutable: worker sort jobs never read mutable render state. */
public record WorldWarpFrame(Vec3 camera, Vec3 center, Vec3 axis, double slope, double distanceOffset, boolean enabled, long epoch) {
    public static final WorldWarpFrame OFF = new WorldWarpFrame(Vec3.ZERO, Vec3.ZERO, new Vec3(0, 0, 1), 0, 0, false, 0);

    public static WorldWarpFrame sample(WorldWarpState state, Vec3 camera, Vec3 eyes, double tick, long epoch) {
        Vec3 center = (state.followEyes() ? eyes : state.center()).subtract(camera);
        // Keep the render origin at the real camera; only the twist's zero-angle slice follows the eyes.
        double distanceOffset = state.followEyes() ? center.dot(state.axis()) : 0;
        return new WorldWarpFrame(camera, center, state.axis(), state.slope(tick), distanceOffset, true, epoch);
    }
}
