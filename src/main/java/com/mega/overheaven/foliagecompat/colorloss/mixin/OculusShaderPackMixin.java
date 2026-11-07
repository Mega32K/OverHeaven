package com.mega.overheaven.foliagecompat.colorloss.mixin;

import com.mega.overheaven.foliagecompat.colorloss.client.ColorLossPackAdapter;
import com.mega.overheaven.foliagecompat.colorloss.client.LayeredColorLossPack;
import java.nio.file.Path;
import net.irisshaders.iris.shaderpack.include.IncludeGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.shaderpack.ShaderPack", remap = false)
public abstract class OculusShaderPackMixin implements LayeredColorLossPack, com.mega.overheaven.foliagecompat.worldwarp.client.WarpedShaderPack {
    @Unique private Path overheaven$root;
    @Unique private boolean overheaven$layered;
    @Unique private boolean overheaven$warped;

    @ModifyArg(method = "<init>(Ljava/nio/file/Path;Ljava/util/Map;Lcom/google/common/collect/ImmutableList;)V",
            at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/shaderpack/include/IncludeGraph;<init>(Ljava/nio/file/Path;Lcom/google/common/collect/ImmutableList;)V"), index = 0)
    private Path overheaven$root(Path root) {
        overheaven$root = root;
        return root;
    }
    @ModifyArg(method = "<init>(Ljava/nio/file/Path;Ljava/util/Map;Lcom/google/common/collect/ImmutableList;)V",
            at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/shaderpack/include/IncludeProcessor;<init>(Lnet/irisshaders/iris/shaderpack/include/IncludeGraph;)V"), index = 0)
    private IncludeGraph overheaven$adapt(IncludeGraph graph) {
        IncludeGraph result = ColorLossPackAdapter.adapt(graph, overheaven$root);
        overheaven$layered = result != graph;
        if (!com.mega.overheaven.foliagecompat.worldwarp.WorldWarpMixinPlugin.supportedRuntime()) return result;
        IncludeGraph warped = com.mega.overheaven.foliagecompat.worldwarp.client.WorldWarpPackAdapter.adapt(result, overheaven$root);
        overheaven$warped = warped != result;
        return warped;
    }
    @Override public boolean overheaven$layeredColorLoss() { return overheaven$layered; }
    @Override public boolean overheaven$worldWarp() { return overheaven$warped; }
}
