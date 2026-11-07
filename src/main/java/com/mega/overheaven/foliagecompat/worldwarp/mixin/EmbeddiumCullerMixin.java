package com.mega.overheaven.foliagecompat.worldwarp.mixin;

import com.mega.overheaven.foliagecompat.worldwarp.client.WorldWarpClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.occlusion.OcclusionCuller", remap = false)
public abstract class EmbeddiumCullerMixin {
    @Inject(method = "getOutwardDirections(Lnet/minecraft/core/SectionPos;Lme/jellysquid/mods/sodium/client/render/chunk/RenderSection;)I", at = @At("HEAD"), cancellable = true)
    private static void overheaven$allNeighbors(CallbackInfoReturnable<Integer> ci) {
        if (WorldWarpClient.worldPass()) ci.setReturnValue(63);
    }
    @Inject(method = "isWithinRenderDistance(Lme/jellysquid/mods/sodium/client/render/viewport/CameraTransform;Lme/jellysquid/mods/sodium/client/render/chunk/RenderSection;F)Z", at = @At("HEAD"), cancellable = true)
    private static void overheaven$loadedRange(CallbackInfoReturnable<Boolean> ci) {
        // Source-space distance cannot reject geometry twisted into view. The swept frustum still culls it.
        if (WorldWarpClient.worldPass()) ci.setReturnValue(true);
    }
    @ModifyVariable(method = "processQueue(Lme/jellysquid/mods/sodium/client/render/chunk/occlusion/OcclusionCuller$Visitor;Lme/jellysquid/mods/sodium/client/render/viewport/Viewport;FZILme/jellysquid/mods/sodium/client/util/collections/ReadQueue;Lme/jellysquid/mods/sodium/client/util/collections/WriteQueue;)V",
            at = @At(value = "LOAD", ordinal = 1), index = 8)
    private static boolean overheaven$traverseInvisible(boolean visible) {
        // 0.3.31: slot 8's first load feeds Visitor.visit; its second load prunes traversal.
        // Keep the actual visibility result, but allow the search to cross invisible source sections.
        return WorldWarpClient.worldPass() || visible;
    }
}
