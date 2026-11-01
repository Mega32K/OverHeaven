package com.mega.overheaven.foliagecompat.shelf;

import com.mega.overheaven.foliagecompat.FoliageCompat;
import java.util.List;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class Shelves {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, FoliageCompat.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FoliageCompat.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, FoliageCompat.MOD_ID);

    public static final List<RegistryObject<ShelfBlock>> VARIANTS = List.of(
            shelf("oak", Blocks.OAK_PLANKS), shelf("spruce", Blocks.SPRUCE_PLANKS),
            shelf("birch", Blocks.BIRCH_PLANKS), shelf("jungle", Blocks.JUNGLE_PLANKS),
            shelf("acacia", Blocks.ACACIA_PLANKS), shelf("dark_oak", Blocks.DARK_OAK_PLANKS),
            shelf("mangrove", Blocks.MANGROVE_PLANKS), shelf("cherry", Blocks.CHERRY_PLANKS),
            shelf("bamboo", Blocks.BAMBOO_PLANKS), shelf("crimson", Blocks.CRIMSON_PLANKS),
            shelf("warped", Blocks.WARPED_PLANKS));

    public static final RegistryObject<BlockEntityType<ShelfBlockEntity>> BLOCK_ENTITY = BLOCK_ENTITIES.register("shelf",
            () -> BlockEntityType.Builder.of(ShelfBlockEntity::new, VARIANTS.stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));

    private Shelves() { }

    private static RegistryObject<ShelfBlock> shelf(String wood, Block material) {
        RegistryObject<ShelfBlock> block = BLOCKS.register(wood + "_shelf",
                () -> new ShelfBlock(BlockBehaviour.Properties.copy(material).strength(2.0F).noOcclusion().pushReaction(PushReaction.BLOCK)));
        ITEMS.register(wood + "_shelf", () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        bus.addListener(Shelves::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.FUNCTIONAL_BLOCKS)) VARIANTS.forEach(event::accept);
    }
}
