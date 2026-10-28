package com.mega.overheaven.foliagecompat.finale.mixin;

import com.mega.overheaven.foliagecompat.finale.FinaleManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Last write gate also covers fluid ticks and direct chunk writes. */
@Mixin(LevelChunk.class)
public abstract class GlassReservationMixin {
    @Inject(method="setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",at=@At("HEAD"),cancellable=true)
    private void overheaven$reserved(BlockPos pos,BlockState state,boolean moving,CallbackInfoReturnable<BlockState> callback) {
        if(FinaleManager.locked(((LevelChunk)(Object)this).getLevel(),pos)) callback.setReturnValue(null);
    }
}
