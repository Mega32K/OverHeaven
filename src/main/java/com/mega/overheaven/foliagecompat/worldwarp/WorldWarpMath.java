package com.mega.overheaven.foliagecompat.worldwarp;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared CPU counterpart of world_warp.glsl. Positions/center are camera-relative; offset locates the zero-angle slice. */
public final class WorldWarpMath {
    private WorldWarpMath() {}
    public static Vec3 rotate(Vec3 v, Vec3 axis, double angle) {
        double c = Math.cos(angle), s = Math.sin(angle);
        return v.scale(c).add(axis.cross(v).scale(s)).add(axis.scale(axis.dot(v) * (1 - c)));
    }
    public static Vec3 warp(Vec3 p, Vec3 center, Vec3 axis, double slope, double distanceOffset) {
        if (slope == 0) return p;
        return center.add(rotate(p.subtract(center), axis, slope * (axis.dot(p) - distanceOffset)));
    }
    public static Vec3 inverse(Vec3 p, Vec3 center, Vec3 axis, double slope, double distanceOffset) {
        return warp(p, center, axis, -slope, distanceOffset);
    }
    public static Vec3 normal(Vec3 p, Vec3 n, Vec3 center, Vec3 axis, double slope, double distanceOffset) {
        Vec3 q = p.subtract(center);
        return rotate(n.subtract(axis.scale(slope * axis.cross(q).dot(n))), axis, slope * (axis.dot(p) - distanceOffset)).normalize();
    }
    /** An analytic full-orbit cylinder bound, including curved edges between the eight corners. */
    public static AABB sweptBounds(AABB box, Vec3 center, Vec3 axis) {
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY, radius2 = 0;
        for (int i = 0; i < 8; i++) {
            Vec3 q = new Vec3((i & 1) == 0 ? box.minX : box.maxX,
                    (i & 2) == 0 ? box.minY : box.maxY, (i & 4) == 0 ? box.minZ : box.maxZ).subtract(center);
            double s = q.dot(axis);
            lo = Math.min(lo, s); hi = Math.max(hi, s);
            radius2 = Math.max(radius2, q.lengthSqr() - s * s);
        }
        double half = (hi - lo) * 0.5, radius = Math.sqrt(Math.max(0, radius2));
        Vec3 mid = center.add(axis.scale((lo + hi) * 0.5));
        double x = Math.abs(axis.x) * half + radius * Math.sqrt(Math.max(0, 1 - axis.x * axis.x)) + 0.01;
        double y = Math.abs(axis.y) * half + radius * Math.sqrt(Math.max(0, 1 - axis.y * axis.y)) + 0.01;
        double z = Math.abs(axis.z) * half + radius * Math.sqrt(Math.max(0, 1 - axis.z * axis.z)) + 0.01;
        return new AABB(mid.x - x, mid.y - y, mid.z - z, mid.x + x, mid.y + y, mid.z + z);
    }
}
