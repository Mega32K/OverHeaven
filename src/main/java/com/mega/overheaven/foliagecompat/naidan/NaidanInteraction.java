package com.mega.overheaven.foliagecompat.naidan;

import com.mega.overheaven.foliagecompat.FoliageCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FoliageCompat.MOD_ID)
public final class NaidanInteraction {
    private NaidanInteraction() { }

    @SubscribeEvent public static void leftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().getBlockState(event.getPos()).is(Naidan.BLOCK.get())
                || event.getEntity().isShiftKeyDown()) return;
        // Cancel on both sides, including creative mode, so a punch never breaks the block.
        event.setCanceled(true);
        if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START
                && event.getLevel() instanceof ServerLevel level
                && !event.getEntity().isSpectator()
                && event.getEntity().canReach(event.getPos(), 1.5)
                && level.mayInteract(event.getEntity(), event.getPos())
                && level.getBlockEntity(event.getPos()) instanceof NaidanBlockEntity naidan) {
            naidan.play(level, NaidanBlockEntity.Performance.LAUGH);
        }
    }
}
