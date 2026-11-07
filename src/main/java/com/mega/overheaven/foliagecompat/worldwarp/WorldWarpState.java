package com.mega.overheaven.foliagecompat.worldwarp;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Server-tick timeline; the shader receives its slope once per rendered frame. */
public record WorldWarpState(ResourceLocation dimension, Vec3 center, Vec3 axis, boolean followEyes,
                             double strength, double periodTicks, long startTick,
                             long fadeStart, double fadeTicks) {
    public double envelope(double tick) {
        if (fadeStart < 0) return strength;
        if (fadeTicks == 0) return 0;
        double t = Math.max(0, Math.min(1, (tick - fadeStart) / fadeTicks));
        return strength * (1 - t * t * (3 - 2 * t));
    }
    public double slope(double tick) {
        double phase = ((tick - startTick) % periodTicks) / periodTicks;
        return 0.03 * envelope(tick) * Math.sin(phase * Math.PI * 2);
    }
    public boolean finished(double tick) { return fadeStart >= 0 && tick >= fadeStart + fadeTicks; }
    public WorldWarpState stop(long tick, double ticks) {
        return new WorldWarpState(dimension, center, axis, followEyes, envelope(tick), periodTicks, startTick, tick, ticks);
    }
    public static Vec3 direction(double yaw, double pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
    }
}
