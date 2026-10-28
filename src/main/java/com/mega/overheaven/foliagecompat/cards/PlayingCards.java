package com.mega.overheaven.foliagecompat.cards;

import java.util.List;

import com.mega.overheaven.foliagecompat.FoliageCompat;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class PlayingCards {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FoliageCompat.MOD_ID);
    private static final List<RegistryObject<Item>> CARDS = List.of(
            card("2"), card("3"), card("4"), card("5"), card("6"), card("7"), card("8"),
            card("9"), card("10"), card("j"), card("q"), card("k"), card("a"));

    private PlayingCards() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(PlayingCards::addToCreativeTab);
    }

    private static RegistryObject<Item> card(String rank) {
        return ITEMS.register("playing_card_" + rank, () -> new Item(new Item.Properties().stacksTo(64)));
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.INGREDIENTS)) {
            CARDS.forEach(event::accept);
        }
    }
}
