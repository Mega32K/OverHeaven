package com.mega.overheaven.foliagecompat.worldwarp.mixin;

import com.mega.overheaven.foliagecompat.worldwarp.client.WorldWarpClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
public abstract class EmbeddiumFacesMixin {
    @Inject(method = "getVisibleFaces(IIIIII)I", at = @At("HEAD"), cancellable = true)
    private static void overheaven$warpFaces(int x, int y, int z, int sx, int sy, int sz, CallbackInfoReturnable<Integer> ci) {
        if (WorldWarpClient.worldPass()) ci.setReturnValue(127); // ModelQuadFacing.ALL: six faces plus unassigned.
    }
}
