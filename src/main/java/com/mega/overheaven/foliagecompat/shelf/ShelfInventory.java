package com.mega.overheaven.foliagecompat.shelf;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;

/** Private storage, deliberately not a Container or an automation capability. */
final class ShelfInventory {
    static final int SIZE = 3;
    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    ItemStack get(int slot) { return items.get(slot); }

    ItemStack exchange(int slot, ItemStack held, boolean creative) {
        ItemStack previous = items.get(slot);
        items.set(slot, held.copy());
        return creative && previous.isEmpty() ? held.copy() : previous;
    }

    void save(CompoundTag tag) { ContainerHelper.saveAllItems(tag, items); }

    void load(CompoundTag tag) {
        items.clear();
        ContainerHelper.loadAllItems(tag, items);
    }

    /** Clear before spawning drops so a repeated removal cannot drop them again. */
    NonNullList<ItemStack> drain() {
        NonNullList<ItemStack> contents = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        for (int i = 0; i < SIZE; i++) contents.set(i, items.set(i, ItemStack.EMPTY));
        return contents;
    }
}
