package com.mega.overheaven.foliagecompat.worldwarp;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

public final class WorldWarpEvents {
    private static final Map<UUID, WorldWarpState> ACTIVE = new HashMap<>();
    public static int start(Collection<ServerPlayer> players, WorldWarpState state, long tick) {
        int count = 0;
        for (var player : players) {
            if (!player.level().dimension().location().equals(state.dimension()) || !WorldWarpNetwork.available(player)) continue;
            ACTIVE.put(player.getUUID(), state); WorldWarpNetwork.send(player, state, tick); count++;
        }
        return count;
    }
    public static int stop(Collection<ServerPlayer> players, long tick, double duration) {
        int count = 0;
        for (var player : players) {
            var state = ACTIVE.get(player.getUUID());
            if (state == null) continue;
            state = duration == 0 ? null : state.stop(tick, duration);
            if (state == null) ACTIVE.remove(player.getUUID()); else ACTIVE.put(player.getUUID(), state);
            WorldWarpNetwork.send(player, state, tick); count++;
        }
        return count;
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent event) { WorldWarpCommands.register(event.getDispatcher()); }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        long tick = server.overworld().getGameTime();
        ACTIVE.entrySet().removeIf(entry -> {
            var player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) return true;
            if (entry.getValue().finished(tick) || !player.level().dimension().location().equals(entry.getValue().dimension())) {
                WorldWarpNetwork.send(player, null, tick); return true;
            }
            return false;
        });
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) { ACTIVE.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && ACTIVE.remove(player.getUUID()) != null)
            WorldWarpNetwork.send(player, null, player.server.overworld().getGameTime());
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) { ACTIVE.clear(); }
}
