package com.mega.overheaven.foliagecompat.worldwarp.mixin;

import com.mega.overheaven.foliagecompat.worldwarp.client.WorldWarpClient;
import com.mojang.blaze3d.vertex.VertexSorting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "net.irisshaders.batchedentityrendering.impl.SegmentedBufferBuilder", remap = false)
public abstract class OculusSortingMixin {
    @ModifyArg(method = {"getBuffer", "getSegments", "getSegmentsForType"},
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/BufferBuilder;setQuadSorting(Lcom/mojang/blaze3d/vertex/VertexSorting;)V", remap = true), index = 0)
    private VertexSorting overheaven$sort(VertexSorting original) { return WorldWarpClient.sorting(original); }
}
