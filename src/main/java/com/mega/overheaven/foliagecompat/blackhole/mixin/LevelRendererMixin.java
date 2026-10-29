package com.mega.overheaven.foliagecompat.blackhole.mixin;

import com.mega.overheaven.foliagecompat.blackhole.client.BlackHoleRenderer;
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

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method="renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V",at=@At("HEAD"))
    private void overheaven$begin(PoseStack pose,float partial,long deadline,boolean outline,Camera camera,GameRenderer renderer,LightTexture light,Matrix4f projection,CallbackInfo callback) {
        com.mega.overheaven.foliagecompat.blackhole.client.FinaleRenderer.INSTANCE.camera(pose.last().pose(),projection,camera);
        BlackHoleRenderer.INSTANCE.begin(pose.last().pose(),projection,camera,partial);
        com.mega.overheaven.foliagecompat.blackhole.client.IllusionTpRenderer.INSTANCE.camera(pose.last().pose(),projection);
    }
    @Inject(method="renderLevel",at=@At(value="CONSTANT",args="stringValue=translucent"),require=2,allow=2)
    private void overheaven$opaque(CallbackInfo callback) { BlackHoleRenderer.INSTANCE.beforeTranslucent(); }
    @Inject(method="renderLevel",at=@At(value="CONSTANT",args="stringValue=string"),require=2,allow=2)
    private void overheaven$surface(CallbackInfo callback) { BlackHoleRenderer.INSTANCE.afterTranslucent(); }
}
