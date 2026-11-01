package com.mega.overheaven.foliagecompat.shelf;

import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

/** Headless storage and interaction tests, not a Minecraft session or renderer test. */
public final class ShelfVerification {
    private static int checks;

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private static int slot(Direction facing, double horizontal) {
        double x = facing == Direction.NORTH ? 1.0 - horizontal : horizontal;
        double z = facing == Direction.EAST ? 1.0 - horizontal : horizontal;
        return ShelfInteraction.slot(facing, facing, x, z, InteractionHand.MAIN_HAND, false);
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        bootstrapRegistries();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int i = 0; i < 3; i++) check(slot(direction, (i + 0.5) / 3) == i, "slot center " + direction + "/" + i);
            check(slot(direction, 0) == 0 && slot(direction, 1) == 2, "outer edges " + direction);
            for (double boundary : new double[]{1.0 / 3, 2.0 / 3}) {
                int next = (int) Math.round(boundary * 3);
                check(slot(direction, boundary - 1.0e-6) == next - 1, "left of slot boundary");
                check(slot(direction, boundary + 1.0e-6) == next, "right of slot boundary");
                check(slot(direction, boundary) == next, "exact boundary selects next slot in every facing");
            }
            check(slot(direction, -0.01) == -1 && slot(direction, 1.01) == -1, "outside block");
            for (Direction face : Direction.values()) {
                if (face != direction) check(ShelfInteraction.slot(direction, face, 0.5, 0.5, InteractionHand.MAIN_HAND, false) == -1, "side/back/top/bottom ignored");
            }
            check(ShelfInteraction.slot(direction, direction, 0.5, 0.5, InteractionHand.OFF_HAND, false) == -1, "off hand cannot exchange twice");
            check(ShelfInteraction.slot(direction, direction, 0.5, 0.5, InteractionHand.MAIN_HAND, true) == -1, "sneaking leaves block placement available");
        }
        check(slot(Direction.NORTH, Double.NaN) == -1, "nonfinite hit rejected");

        ShelfInventory inventory = new ShelfInventory();
        ItemStack apples = new ItemStack(Items.APPLE, 37);
        apples.setHoverName(Component.literal("Shelf test"));
        apples.getOrCreateTag().putString("custom_data", "preserve me");
        check(inventory.exchange(0, apples, false).isEmpty(), "empty shelf consumes survival hand");
        check(ItemStack.matches(inventory.get(0), apples), "whole stack and metadata stored");
        apples.setCount(1);
        apples.getOrCreateTag().putString("custom_data", "changed outside shelf");
        check(inventory.get(0).getCount() == 37 && inventory.get(0).getTag().getString("custom_data").equals("preserve me"), "no mutable alias to former hand");
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.setDamageValue(42);
        sword.enchant(Enchantments.SHARPNESS, 3);
        sword.setHoverName(Component.literal("Named sword"));
        ItemStack old = inventory.exchange(0, sword, false);
        check(old.getCount() == 37 && old.is(Items.APPLE), "occupied shelf returns former stack");
        check(ItemStack.matches(inventory.get(0), sword), "durability/enchantments/name preserved");
        check(ItemStack.matches(inventory.exchange(0, ItemStack.EMPTY, false), sword) && inventory.get(0).isEmpty(), "empty hand takes item");
        check(inventory.exchange(0, ItemStack.EMPTY, false).isEmpty(), "both empty remains empty");

        ItemStack creative = new ItemStack(Items.STONE, 64);
        ItemStack retained = inventory.exchange(1, creative, true);
        check(ItemStack.matches(retained, creative) && ItemStack.matches(inventory.get(1), creative), "creative empty slot retains hand");
        retained.setCount(2);
        check(inventory.get(1).getCount() == 64, "creative retained hand is independent");
        check(inventory.exchange(1, sword, true).getCount() == 64, "creative occupied slot returns previous contents");
        check(ItemStack.matches(inventory.exchange(1, ItemStack.EMPTY, true), sword), "creative empty hand takes contents");

        // Same item type must swap, never merge, and the other slots remain untouched.
        inventory.exchange(0, new ItemStack(Items.APPLE, 12), false);
        inventory.exchange(1, sword, false);
        inventory.exchange(2, new ItemStack(Items.STONE, 64), false);
        check(inventory.exchange(0, new ItemStack(Items.APPLE, 21), false).getCount() == 12 && inventory.get(0).getCount() == 21, "same item exchanges rather than merges");
        check(ItemStack.matches(inventory.get(1), sword) && inventory.get(2).getCount() == 64, "exchange affects one slot only");
        CompoundTag saved = new CompoundTag();
        inventory.save(saved);
        ShelfInventory loaded = new ShelfInventory();
        loaded.load(saved);
        for (int i = 0; i < 3; i++) check(ItemStack.matches(inventory.get(i), loaded.get(i)), "NBT slot round trip " + i);
        var drops = loaded.drain();
        for (int i = 0; i < 3; i++) check(ItemStack.matches(drops.get(i), inventory.get(i)) && loaded.get(i).isEmpty(), "drop ownership transferred " + i);
        check(loaded.drain().stream().allMatch(ItemStack::isEmpty), "repeated removal has no drops");
        loaded.load(saved);
        loaded.load(new CompoundTag());
        for (int i = 0; i < 3; i++) check(loaded.get(i).isEmpty(), "empty update clears stale slot " + i);
        try {
            inventory.exchange(3, sword, false);
            throw new AssertionError("out-of-range exchange accepted");
        } catch (IndexOutOfBoundsException expected) { checks++; }
        System.out.println("Shelf storage/interaction checks passed: " + checks);
    }

    private static void bootstrapRegistries() {
        try {
            Bootstrap.bootStrap();
        } catch (ExceptionInInitializerError error) {
            // Forge 47.4.10 initializes registries and takes vanillaSnapshot BEFORE
            // NetworkHooks.init. A plain JavaExec has no ModLauncher event transformer,
            // so NetworkEvent lacks its generated no-arg constructor. Accept ONLY this
            // known networking-bootstrap failure; storage tests need the real registries,
            // not a running handshake channel. Never suppress other bootstrap failures.
            Throwable cause = error;
            while (cause.getCause() != null) cause = cause.getCause();
            if (!(cause instanceof NoSuchMethodException)
                    || !"net.minecraftforge.network.NetworkEvent.<init>()".equals(cause.getMessage())
                    || error.getStackTrace().length == 0
                    || !error.getStackTrace()[0].getClassName().equals("net.minecraftforge.network.NetworkHooks")) throw error;
            System.out.println("Headless registry bootstrap complete; Forge handshake bootstrap unavailable without ModLauncher (not tested).");
        }
        check(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Items.APPLE).toString().equals("minecraft:apple"), "real item registry available");
    }
}
