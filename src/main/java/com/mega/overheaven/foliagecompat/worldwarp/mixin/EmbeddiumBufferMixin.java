package com.mega.overheaven.foliagecompat.worldwarp.mixin;

import com.mega.overheaven.foliagecompat.worldwarp.client.WarpNativeBuffer;
import com.mega.overheaven.foliagecompat.worldwarp.client.WorldWarpFrame;
import java.nio.ByteBuffer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.util.NativeBuffer", remap = false)
public abstract class EmbeddiumBufferMixin implements WarpNativeBuffer {
    @Shadow public abstract ByteBuffer getDirectBuffer();
    @Unique private WorldWarpFrame overheaven$frame;
    @Unique private Vec3 overheaven$camera;
    @Override public void overheaven$sortContext(WorldWarpFrame frame, Vec3 cameraLocal) {
        overheaven$frame = frame;
        overheaven$camera = cameraLocal;
    }
    @Override public WorldWarpFrame overheaven$sortFrame() { return overheaven$frame; }
    @Override public Vec3 overheaven$sortCamera() { return overheaven$camera; }
}
