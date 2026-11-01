package com.mega.overheaven.foliagecompat.blackhole.mixin;

import com.mega.overheaven.foliagecompat.blackhole.client.IllusionTpRenderer;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only applied by the existing exact-version Oculus 1.8.0 guard. */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap = false)
public abstract class OculusIllusionPipelineMixin {
    @Shadow @Final private RenderTargets renderTargets;
    @Inject(method = "beginLevelRendering()V", at = @At("TAIL"))
    private void overheaven$illusionDepth(CallbackInfo callback) {
        IllusionTpRenderer.INSTANCE.beginOculus(renderTargets.getDepthTexture());
    }
    @Inject(method = "finalizeLevelRendering()V", at = @At("HEAD"))
    private void overheaven$illusionWorld(CallbackInfo callback) {
        IllusionTpRenderer.INSTANCE.oculusWorld(renderTargets.getDepthTexture(),
                CapturedRenderingState.INSTANCE.getGbufferModelView(), CapturedRenderingState.INSTANCE.getGbufferProjection());
    }
}
