package com.mega.overheaven.foliagecompat.worldwarp.mixin;

import com.mega.overheaven.foliagecompat.worldwarp.client.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask", remap = false)
public abstract class EmbeddiumMeshingTaskMixin {
    @Unique private WarpSortContext overheaven$context;
    @Inject(method = "<init>", at = @At("RETURN"))
    private void overheaven$capture(@Coerce Object section, @Coerce Object region, int frame, CallbackInfo ci) {
        overheaven$context = new WarpSortContext(WorldWarpClient.frame(), (WarpSection)section);
    }
    @ModifyArgs(method = "execute(Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lme/jellysquid/mods/sodium/client/util/task/CancellationToken;)Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
            at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBufferSorter;sort(Lme/jellysquid/mods/sodium/client/util/NativeBuffer;Lorg/embeddedt/embeddium/render/chunk/sorting/TranslucentQuadAnalyzer$SortState;FFF)Lme/jellysquid/mods/sodium/client/util/NativeBuffer;"))
    private void overheaven$context(Args args) { overheaven$context.attach((WarpNativeBuffer)args.get(0)); }
    @Inject(method = "execute(Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lme/jellysquid/mods/sodium/client/util/task/CancellationToken;)Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;", at = @At("RETURN"))
    private void overheaven$tag(CallbackInfoReturnable<Object> ci) { overheaven$context.tag(ci.getReturnValue()); }
}
