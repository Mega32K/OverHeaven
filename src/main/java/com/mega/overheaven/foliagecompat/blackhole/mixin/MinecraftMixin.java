package com.mega.overheaven.foliagecompat.blackhole.mixin;

import com.mega.overheaven.foliagecompat.blackhole.client.BlackHoleRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method="close()V",at=@At("HEAD"))
    private void overheaven$close(CallbackInfo callback) { BlackHoleRenderer.INSTANCE.close(); com.mega.overheaven.foliagecompat.blackhole.client.FinaleClient.clear(); }
}
