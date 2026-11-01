package com.mega.overheaven.foliagecompat.illusiontp.mixin;

import com.mega.overheaven.foliagecompat.illusiontp.IllusionTpMovement;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMovementMixin {
    @Inject(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), cancellable = true)
    private void overheaven$illusionLock(MoverType type, Vec3 displacement, CallbackInfo callback) {
        if (IllusionTpMovement.locked((Entity) (Object) this)) callback.cancel();
    }
}
