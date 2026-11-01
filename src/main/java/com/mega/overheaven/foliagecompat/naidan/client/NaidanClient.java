package com.mega.overheaven.foliagecompat.naidan.client;

import com.mega.overheaven.foliagecompat.FoliageCompat;
import com.mega.overheaven.foliagecompat.naidan.Naidan;
import com.mega.overheaven.foliagecompat.naidan.NaidanBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

@Mod.EventBusSubscriber(modid = FoliageCompat.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NaidanClient {
    private NaidanClient() { }

    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(Naidan.BLOCK_ENTITY.get(), context -> new GeoBlockRenderer<>(
                new DefaultedBlockGeoModel<NaidanBlockEntity>(new ResourceLocation(FoliageCompat.MOD_ID, "naidan"))));
    }
}
