package com.mega.overheaven.foliagecompat.worldwarp.client;

import com.mega.overheaven.foliagecompat.worldwarp.WorldWarpMath;
import net.minecraft.world.phys.Vec3;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import it.unimi.dsi.fastutil.ints.IntArrays;

public final class WorldWarpSorting {
    private WorldWarpSorting() {}
    public static boolean distances(WorldWarpFrame frame, float[] centers, float[] distances, int count, Vec3 cameraLocal) {
        if (!frame.enabled()) return false;
        for (int i = 0; i < count; i++) {
            Vec3 p = new Vec3(centers[3 * i], centers[3 * i + 1], centers[3 * i + 2]).subtract(cameraLocal);
            distances[i] = (float)WorldWarpMath.warp(p, frame.center(), frame.axis(), frame.slope(), frame.distanceOffset()).lengthSqr();
        }
        return true;
    }
    public static void indices(ByteBuffer buffer, float[] centers, WorldWarpFrame frame, Vec3 cameraLocal) {
        int count = centers.length / 3;
        int[] order = new int[count];
        for (int i = 0; i < count; i++) order[i] = i;
        if (frame.enabled()) {
            float[] distances = new float[count];
            distances(frame, centers, distances, count, cameraLocal);
            IntArrays.mergeSort(order, (a, b) -> Float.compare(distances[b], distances[a]));
        }
        // Same quad index layout as Embeddium; only the index buffer is touched.
        ByteBuffer indices = buffer.duplicate().order(ByteOrder.nativeOrder());
        indices.clear();
        for (int quad : order) {
            int first = quad * 4;
            indices.putInt(first).putInt(first + 1).putInt(first + 2);
            indices.putInt(first + 2).putInt(first + 3).putInt(first);
        }
    }
}
