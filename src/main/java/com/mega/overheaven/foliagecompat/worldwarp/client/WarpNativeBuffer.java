package com.mega.overheaven.foliagecompat.worldwarp.client;

import java.nio.ByteBuffer;
import net.minecraft.world.phys.Vec3;

public interface WarpNativeBuffer {
    ByteBuffer getDirectBuffer();
    void overheaven$sortContext(WorldWarpFrame frame, Vec3 cameraLocal);
    WorldWarpFrame overheaven$sortFrame();
    Vec3 overheaven$sortCamera();
}
