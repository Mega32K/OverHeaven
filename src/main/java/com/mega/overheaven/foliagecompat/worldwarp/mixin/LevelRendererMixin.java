package com.mega.overheaven.foliagecompat.worldwarp.mixin;

import com.mega.overheaven.foliagecompat.worldwarp.client.WorldWarpClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelRenderer.class, priority = 1100)
public abstract class LevelRendererMixin {
    @Inject(method = "renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V", at = @At("HEAD"))
    private void overheaven$warpBegin(PoseStack pose, float partial, long deadline, boolean outline, Camera camera,
                                     GameRenderer renderer, LightTexture light, Matrix4f projection, CallbackInfo ci) {
        WorldWarpClient.begin(camera, partial, pose.last().pose());
    }
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void overheaven$warpEnd(CallbackInfo ci) { WorldWarpClient.end(); }
}
