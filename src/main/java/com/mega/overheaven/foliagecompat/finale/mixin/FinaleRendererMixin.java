package com.mega.overheaven.foliagecompat.finale.mixin;

import com.mega.overheaven.foliagecompat.blackhole.client.FinaleRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class FinaleRendererMixin {
    // Verified Forge 47.4.10 bytecode: world + postEffect, then the first GUI depth clear.
    @Inject(method="render(FJZ)V",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V",ordinal=0))
    private void overheaven$finale(float partial,long deadline,boolean renderLevel,CallbackInfo callback) { FinaleRenderer.INSTANCE.render(partial); }
}
