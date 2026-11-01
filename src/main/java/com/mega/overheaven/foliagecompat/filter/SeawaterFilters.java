package com.mega.overheaven.foliagecompat.filter;

import com.mega.overheaven.foliagecompat.FoliageCompat;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class SeawaterFilters {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FoliageCompat.MOD_ID);
    public static final RegistryObject<Item> SEAWATER_FILTER = ITEMS.register("seawater_filter", SeawaterFilterItem::new);

    private SeawaterFilters() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(SeawaterFilters::addToCreativeTab);
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) {
            event.accept(SEAWATER_FILTER);
        }
    }
}
