package com.mega.overheaven.foliagecompat.worldwarp.mixin;

import com.mega.overheaven.foliagecompat.worldwarp.client.WorldWarpClient;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Frustum.class)
public abstract class FrustumMixin {
    @ModifyVariable(method = "isVisible(Lnet/minecraft/world/phys/AABB;)Z", at = @At("HEAD"), argsOnly = true)
    private AABB overheaven$warpBounds(AABB box) { return WorldWarpClient.bounds(box, false); }
}
