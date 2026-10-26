package com.mega.overheaven.foliagecompat.blackhole.client;

import com.mega.overheaven.foliagecompat.FoliageCompat;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=FoliageCompat.MOD_ID,value=Dist.CLIENT)
public final class BlackHoleRenderEvents {
    @SubscribeEvent public static void stage(RenderLevelStageEvent event) {
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_LEVEL) { BlackHoleRenderer.INSTANCE.finishVanilla(); OptionalShaders.completed(); }
    }
}
