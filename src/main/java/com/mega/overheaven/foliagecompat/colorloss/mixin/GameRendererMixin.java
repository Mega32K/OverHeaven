package com.mega.overheaven.foliagecompat.colorloss.mixin;

import com.mega.overheaven.foliagecompat.blackhole.client.ColorLossRenderer;
import com.mega.overheaven.foliagecompat.colorloss.client.ColorLossClient;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render(FJZ)V", at = @At("HEAD"))
    private void overheaven$colorLossFrame(float partial, long deadline, boolean renderLevel, CallbackInfo callback) {
        ColorLossRenderer.INSTANCE.beginFrame(partial);
    }
    // Forge 47.4.10: returning to render guarantees every renderLevel TAIL callback has finished,
    // including Oculus 1.8.0's final color-space conversion and EndingLibrary 2.2.1fix's levelEffect.
    // Vanilla post processing, OverHeaven finale / teleport composites and HUD still follow this pass.
    @Inject(method = "render(FJZ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V",
            shift = At.Shift.AFTER), require = 1, allow = 1)
    private void overheaven$colorLossAfterShaders(float partial, long deadline, boolean renderLevel, CallbackInfo callback) {
        ColorLossRenderer.INSTANCE.render(partial);
    }
    @Inject(method = "close()V", at = @At("HEAD"))
    private void overheaven$colorLossClose(CallbackInfo callback) { ColorLossClient.clear(); }
}
