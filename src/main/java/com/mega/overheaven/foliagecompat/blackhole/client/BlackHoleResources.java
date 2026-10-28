package com.mega.overheaven.foliagecompat.blackhole.client;

import com.mega.overheaven.foliagecompat.FoliageCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=FoliageCompat.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class BlackHoleResources {
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener)manager -> Minecraft.getInstance().execute(()-> { BlackHoleRenderer.INSTANCE.close(); FinaleRenderer.INSTANCE.close(); FinaleAudio.close(); FinaleClient.resetFeedback(); }));
    }
}
