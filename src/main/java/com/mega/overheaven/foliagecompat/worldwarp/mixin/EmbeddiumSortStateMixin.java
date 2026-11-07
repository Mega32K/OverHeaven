package com.mega.overheaven.foliagecompat.worldwarp.mixin;

import com.mega.overheaven.foliagecompat.worldwarp.client.*;
import java.util.BitSet;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "org.embeddedt.embeddium.render.chunk.sorting.TranslucentQuadAnalyzer$SortState", remap = false)
public abstract class EmbeddiumSortStateMixin implements WarpSortState {
    @Shadow public abstract float[] centers();
    @Unique private boolean overheaven$unsorted;
    @Unique private volatile boolean overheaven$needsSort;
    @Inject(method = "<init>", at = @At("RETURN"))
    private void overheaven$level(@Coerce Object level, float[] centers, BitSet signs, Vector3f normal, CallbackInfo ci) {
        overheaven$unsorted = ((Enum<?>)level).name().equals("NONE");
    }
    @Override public boolean overheaven$unsorted() { return overheaven$unsorted; }
    @Override public void overheaven$needsSort(boolean value) { overheaven$needsSort = value; }
    @Inject(method = "requiresDynamicSorting()Z", at = @At("HEAD"), cancellable = true)
    private void overheaven$warpDynamic(CallbackInfoReturnable<Boolean> ci) {
        if ((WorldWarpClient.frame().enabled() || overheaven$needsSort) && centers() != null && centers().length >= 6) ci.setReturnValue(true);
    }
    @Inject(method = "compactForStorage()Lorg/embeddedt/embeddium/render/chunk/sorting/TranslucentQuadAnalyzer$SortState;", at = @At("HEAD"), cancellable = true)
    private void overheaven$keepCenters(CallbackInfoReturnable<Object> ci) {
        // Keep CPU centers so a later command can sort STATIC geometry without rebuilding vertices.
        ci.setReturnValue(this);
    }
}
