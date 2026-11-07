package com.mega.overheaven.foliagecompat.worldwarp.mixin;

import com.mega.overheaven.foliagecompat.worldwarp.client.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.RenderSection", remap = false)
public abstract class EmbeddiumSectionMixin implements WarpSection {
    @Shadow public double lastCameraX;
    @Shadow public abstract int getOriginX();
    @Shadow public abstract int getOriginY();
    @Shadow public abstract int getOriginZ();
    @Shadow public abstract boolean isBuilt();
    @Shadow public abstract boolean isDisposed();
    @Unique private WarpSortState overheaven$sortState;
    @Unique private long overheaven$sortedEpoch;
    @Inject(method = "setSortState(Lorg/embeddedt/embeddium/render/chunk/sorting/TranslucentQuadAnalyzer$SortState;)V", at = @At("HEAD"))
    private void overheaven$capture(@Coerce Object state, CallbackInfo ci) {
        overheaven$sortState = (WarpSortState)state;
        overheaven$sortedEpoch = WorldWarpClient.frame().enabled() ? -1 : WorldWarpClient.frame().epoch();
    }
    @Override public boolean overheaven$invalidateWarpSort(WorldWarpFrame frame) {
        if (!isBuilt() || isDisposed() || overheaven$sortState == null || overheaven$sortState.centers() == null
                || overheaven$sortState.centers().length < 6) return false;
        boolean needed = frame.enabled() || overheaven$sortedEpoch != frame.epoch();
        overheaven$sortState.overheaven$needsSort(needed);
        if (!needed) return false;
        lastCameraX = Double.POSITIVE_INFINITY;
        return true;
    }
    @Override public void overheaven$warpSorted(long epoch) {
        overheaven$sortedEpoch = epoch;
        overheaven$invalidateWarpSort(WorldWarpClient.frame());
    }
}
