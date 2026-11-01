package com.mega.overheaven.foliagecompat.shelf;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class ShelfBlockEntity extends BlockEntity {
    private final ShelfInventory inventory = new ShelfInventory();

    public ShelfBlockEntity(BlockPos pos, BlockState state) {
        super(Shelves.BLOCK_ENTITY.get(), pos, state);
    }

    /** Read-only view for the renderer; callers must not mutate it. */
    public ItemStack displayedItem(int slot) { return inventory.get(slot); }

    ItemStack exchange(int slot, ItemStack held, boolean creative) {
        ItemStack returned = inventory.exchange(slot, held, creative);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        return returned;
    }

    void dropContents() {
        if (level == null || level.isClientSide) return;
        var contents = inventory.drain();
        setChanged();
        Containers.dropContents(level, worldPosition, contents);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        inventory.load(tag);
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        inventory.save(tag);
    }

    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
