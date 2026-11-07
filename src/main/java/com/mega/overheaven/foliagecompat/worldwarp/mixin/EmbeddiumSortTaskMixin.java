package com.mega.overheaven.foliagecompat.worldwarp.mixin;

import com.mega.overheaven.foliagecompat.worldwarp.client.*;
import java.util.Map;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderSortTask", remap = false)
public abstract class EmbeddiumSortTaskMixin {
    @Unique private WarpSortContext overheaven$context;
    @Inject(method = "<init>", at = @At("RETURN"))
    private void overheaven$capture(@Coerce Object section, float x, float y, float z, int frame, Map<?, ?> meshes, CallbackInfo ci) {
        overheaven$context = new WarpSortContext(WorldWarpClient.frame(), (WarpSection)section);
    }
    @ModifyArgs(method = "execute(Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lme/jellysquid/mods/sodium/client/util/task/CancellationToken;)Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
            at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBufferSorter;sort(Lme/jellysquid/mods/sodium/client/util/NativeBuffer;Lorg/embeddedt/embeddium/render/chunk/sorting/TranslucentQuadAnalyzer$SortState;FFF)Lme/jellysquid/mods/sodium/client/util/NativeBuffer;"))
    private void overheaven$context(Args args) { overheaven$context.attach((WarpNativeBuffer)args.get(0)); }
    @Inject(method = "execute(Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lme/jellysquid/mods/sodium/client/util/task/CancellationToken;)Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;", at = @At("RETURN"))
    private void overheaven$end(CallbackInfoReturnable<Object> ci) {
        overheaven$context.tag(ci.getReturnValue());
    }
}
