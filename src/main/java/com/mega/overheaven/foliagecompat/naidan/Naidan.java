package com.mega.overheaven.foliagecompat.naidan;

import com.mega.overheaven.foliagecompat.FoliageCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class Naidan {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, FoliageCompat.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FoliageCompat.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, FoliageCompat.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, FoliageCompat.MOD_ID);

    public static final RegistryObject<NaidanBlock> BLOCK = BLOCKS.register("naidan", () -> new NaidanBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.6F)
                    .sound(SoundType.WOOL).noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<Item> ITEM = ITEMS.register("naidan", () -> new BlockItem(BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<NaidanBlockEntity>> BLOCK_ENTITY = BLOCK_ENTITIES.register("naidan",
            () -> BlockEntityType.Builder.of(NaidanBlockEntity::new, BLOCK.get()).build(null));
    public static final RegistryObject<SoundEvent> ANDI = sound("naidan.andi");
    public static final RegistryObject<SoundEvent> LAUGH = sound("naidan.laugh");

    private Naidan() { }

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FoliageCompat.MOD_ID, name)));
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        SOUNDS.register(bus);
        bus.addListener(Naidan::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.FUNCTIONAL_BLOCKS)) event.accept(ITEM);
    }
}
