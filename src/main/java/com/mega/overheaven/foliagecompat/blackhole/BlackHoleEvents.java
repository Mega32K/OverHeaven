package com.mega.overheaven.foliagecompat.blackhole;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class BlackHoleEvents {
    @SubscribeEvent public void commands(RegisterCommandsEvent event) { BlackHoleCommands.register(event.getDispatcher()); }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) { if (event.getEntity() instanceof ServerPlayer player) BlackHoleNetwork.snapshot(player); }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer player) BlackHoleNetwork.snapshot(player); }
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event) { if (event.getEntity() instanceof ServerPlayer player) BlackHoleNetwork.snapshot(player); }
}
