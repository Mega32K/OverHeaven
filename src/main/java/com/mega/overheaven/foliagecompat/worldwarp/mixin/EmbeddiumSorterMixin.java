package com.mega.overheaven.foliagecompat.worldwarp.mixin;

import com.mega.overheaven.foliagecompat.worldwarp.client.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBufferSorter", remap = false)
public abstract class EmbeddiumSorterMixin {
    @Inject(method = "sort(Lme/jellysquid/mods/sodium/client/util/NativeBuffer;Lorg/embeddedt/embeddium/render/chunk/sorting/TranslucentQuadAnalyzer$SortState;FFF)Lme/jellysquid/mods/sodium/client/util/NativeBuffer;", at = @At("HEAD"), cancellable = true)
    private static void overheaven$warpSort(@Coerce Object buffer, @Coerce Object sortState, float x, float y, float z, CallbackInfoReturnable<Object> ci) {
        if (sortState == null) return;
        var state = (WarpSortState)sortState;
        if (state.centers() == null || state.centers().length < 3) return;
        var nativeBuffer = (WarpNativeBuffer)buffer;
        var frame = nativeBuffer.overheaven$sortFrame();
        if (frame == null) return;
        // NONE normally returns an already initialized buffer; a restore sort task allocates a new one.
        if (!frame.enabled() && !state.overheaven$unsorted()) return;
        WorldWarpSorting.indices(nativeBuffer.getDirectBuffer(), state.centers(), frame, nativeBuffer.overheaven$sortCamera());
        ci.setReturnValue(buffer);
    }
}
