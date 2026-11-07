package com.mega.overheaven.foliagecompat.worldwarp.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Pseudo
@Mixin(targets = "org.embeddedt.embeddium.render.chunk.sorting.TranslucentQuadAnalyzer", remap = false)
public abstract class EmbeddiumAnalyzerMixin {
    @Unique private boolean overheaven$coplanar;
    @Inject(method = "getSortState()Lorg/embeddedt/embeddium/render/chunk/sorting/TranslucentQuadAnalyzer$SortState;", at = @At("HEAD"))
    private void overheaven$reset(CallbackInfoReturnable<Object> ci) { overheaven$coplanar = false; }
    @Inject(method = "areAllQuadsOnSamePlane([F)Z", at = @At("RETURN"), cancellable = true)
    private void overheaven$retainCenters(CallbackInfoReturnable<Boolean> ci) {
        overheaven$coplanar = ci.getReturnValue();
        // Let the host construct an individual state instead of returning its centerless NONE singleton.
        if (overheaven$coplanar) ci.setReturnValue(false);
    }
    @ModifyArgs(method = "getSortState()Lorg/embeddedt/embeddium/render/chunk/sorting/TranslucentQuadAnalyzer$SortState;",
            at = @At(value = "INVOKE", target = "Lorg/embeddedt/embeddium/render/chunk/sorting/TranslucentQuadAnalyzer$SortState;<init>(Lorg/embeddedt/embeddium/render/chunk/sorting/TranslucentQuadAnalyzer$Level;[FLjava/util/BitSet;Lorg/joml/Vector3f;)V"))
    private void overheaven$originalLevel(Args args) {
        if (overheaven$coplanar) {
            for (Enum<?> level : ((Enum<?>)args.get(0)).getDeclaringClass().getEnumConstants())
                if (level.name().equals("NONE")) args.set(0, level);
        }
    }
}
