package com.mega.overheaven.foliagecompat.filter;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

public final class SeawaterFilterItem extends Item {
    public SeawaterFilterItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState ice = level.getBlockState(pos);
        if (!ice.is(BlockTags.ICE)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            BlockState water = Blocks.WATER.defaultBlockState();
            level.setBlockAndUpdate(pos, water);
            level.levelEvent(2001, pos, Block.getId(ice));
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(context.getPlayer(), water));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
