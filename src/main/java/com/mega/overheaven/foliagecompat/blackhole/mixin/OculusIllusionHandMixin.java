package com.mega.overheaven.foliagecompat.blackhole.mixin;

import com.mega.overheaven.foliagecompat.blackhole.client.IllusionTpRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pathways.HandRenderer", remap = false)
public abstract class OculusIllusionHandMixin {
    @Inject(method = {
            "renderSolid(Lcom/mojang/blaze3d/vertex/PoseStack;FLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/irisshaders/iris/pipeline/WorldRenderingPipeline;)V",
            "renderTranslucent(Lcom/mojang/blaze3d/vertex/PoseStack;FLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/irisshaders/iris/pipeline/WorldRenderingPipeline;)V"
    }, at = @At("HEAD"))
    private void overheaven$illusionBeforeHand(CallbackInfo callback) { IllusionTpRenderer.INSTANCE.beforeOculusHand(); }
    @Inject(method = {
            "renderSolid(Lcom/mojang/blaze3d/vertex/PoseStack;FLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/irisshaders/iris/pipeline/WorldRenderingPipeline;)V",
            "renderTranslucent(Lcom/mojang/blaze3d/vertex/PoseStack;FLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/irisshaders/iris/pipeline/WorldRenderingPipeline;)V"
    }, at = @At("RETURN"))
    private void overheaven$illusionAfterHand(CallbackInfo callback) { IllusionTpRenderer.INSTANCE.afterOculusHand(); }
}
