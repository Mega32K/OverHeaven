package com.mega.overheaven.foliagecompat.worldwarp.mixin;

import com.mega.overheaven.foliagecompat.worldwarp.client.WorldWarpClient;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.viewport.Viewport", remap = false)
public abstract class EmbeddiumViewportMixin {
    @ModifyArgs(method = {"isBoxVisible(Lnet/minecraft/world/phys/AABB;)Z", "isBoxVisible(IIIFFF)Z"},
            at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/viewport/frustum/Frustum;testAab(FFFFFF)Z"))
    private void overheaven$warpBounds(Args args) {
        if (!WorldWarpClient.worldPass()) return;
        AABB b = WorldWarpClient.bounds(new AABB((float)args.get(0), (float)args.get(1), (float)args.get(2),
                (float)args.get(3), (float)args.get(4), (float)args.get(5)), true);
        args.setAll((float)b.minX, (float)b.minY, (float)b.minZ, (float)b.maxX, (float)b.maxY, (float)b.maxZ);
    }
}
