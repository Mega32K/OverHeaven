package com.mega.overheaven.foliagecompat.illusiontp.mixin;

import com.mega.overheaven.foliagecompat.blackhole.client.IllusionTpRenderer;
import com.mega.overheaven.foliagecompat.illusiontp.client.IllusionTpClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render(FJZ)V", at = @At("HEAD"))
    private void overheaven$illusionFrame(float partial, long deadline, boolean renderLevel, CallbackInfo callback) {
        IllusionTpRenderer.INSTANCE.beginFrame();
    }
    // Forge 47.4.10: AFTER_LEVEL has completed, but the hand depth clear has not run yet (also works with F1).
    @Inject(method = "renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At(value = "CONSTANT", args = "stringValue=hand"))
    private void overheaven$illusionWorld(float partial, long deadline, PoseStack pose, CallbackInfo callback) {
        IllusionTpRenderer.INSTANCE.vanillaWorld();
    }
    @Inject(method = "renderItemInHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/Camera;F)V", at = @At("RETURN"))
    private void overheaven$illusionHand(PoseStack pose, Camera camera, float partial, CallbackInfo callback) {
        IllusionTpRenderer.INSTANCE.vanillaHand();
    }
    // EndingLibrary 2.2.1fix injects BEFORE this constructor. HUD is drawn after it.
    @Inject(method = "render(FJZ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;<init>(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V", shift = At.Shift.AFTER))
    private void overheaven$illusionComposite(float partial, long deadline, boolean renderLevel, CallbackInfo callback) {
        IllusionTpRenderer.INSTANCE.render(partial);
    }
    @Inject(method = "close()V", at = @At("HEAD"))
    private void overheaven$illusionClose(CallbackInfo callback) { IllusionTpClient.abort(); }
}
