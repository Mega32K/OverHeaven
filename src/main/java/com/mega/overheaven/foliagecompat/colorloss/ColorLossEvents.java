package com.mega.overheaven.foliagecompat.colorloss;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ColorLossEvents {
    @SubscribeEvent public void commands(RegisterCommandsEvent event) { ColorLossCommands.register(event.getDispatcher()); }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) { sync(event); }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { sync(event); }
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event) { sync(event); }
    private void sync(PlayerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ColorLossNetwork.sync(player);
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = event.getServer(); long now = server.overworld().getGameTime();
        if (now % 20 != 0) return;
        var store = ColorLossStore.get(server);
        for (var player : server.getPlayerList().getPlayers())
            if (store.forPlayer(player.getUUID()).running(now - 20)) ColorLossNetwork.sync(player);
    }
}
