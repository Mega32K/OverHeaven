package com.mega.overheaven.foliagecompat.eel;

import com.mega.overheaven.foliagecompat.FoliageCompat;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ElectricEelEngines {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, FoliageCompat.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FoliageCompat.MOD_ID);

    public static final RegistryObject<ElectricEelEngineBlock> BLOCK = BLOCKS.register("electric_eel_engine",
            () -> new ElectricEelEngineBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.0F, 6.0F).noOcclusion()));
    public static final RegistryObject<Item> ITEM = ITEMS.register("electric_eel_engine",
            () -> new BlockItem(BLOCK.get(), new Item.Properties()));

    private ElectricEelEngines() { }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        bus.addListener(ElectricEelEngines::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.REDSTONE_BLOCKS)) event.accept(ITEM);
    }
}
