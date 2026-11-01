package com.mega.overheaven.foliagecompat.illusiontp;

import com.mega.overheaven.foliagecompat.PatchLog;
import com.mega.overheaven.foliagecompat.pixeltp.PixelTpManager;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Server-thread-owned destinations and movement locks; client replies contain only a run nonce. */
public final class IllusionTpManager {
    private static final Map<MinecraftServer, Map<UUID, Pending>> RUNS = new WeakHashMap<>();
    private static final int TIMEOUT_TICKS = 1200;

    public static boolean isActive(ServerPlayer player) {
        var active = RUNS.get(player.server);
        return active != null && active.containsKey(player.getUUID());
    }
    private static void validate(Vec3 position) {
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)
                || !Level.isInSpawnableBounds(BlockPos.containing(position)))
            throw new IllegalArgumentException("illusionTp: destination is outside the world's coordinate limits");
    }
    public static void schedule(Collection<ServerPlayer> players, ServerLevel level, Vec3 position) {
        validate(position);
        var active = RUNS.computeIfAbsent(level.getServer(), ignored -> new HashMap<>());
        for (ServerPlayer player : players) {
            if (!player.isAlive()) throw new IllegalStateException("illusionTp: target player is not alive");
            if (!IllusionTpNetwork.available(player))
                throw new IllegalStateException("illusionTp: " + player.getGameProfile().getName() + " needs this mod's illusionTp client support");
            if (isActive(player) || PixelTpManager.isActive(player))
                throw new IllegalStateException("illusionTp: " + player.getGameProfile().getName() + " already has an active teleport transition");
        }
        for (ServerPlayer player : players) {
            player.stopRiding();
            Pending pending = new Pending(player, level, position);
            active.put(player.getUUID(), pending);
            stopMotion(player);
            pending.send(IllusionTpNetwork.BEGIN);
        }
    }
    public static void reply(ServerPlayer player, IllusionTpNetwork.Reply reply) {
        var active = RUNS.get(player.server);
        Pending pending = active == null ? null : active.get(player.getUUID());
        if (pending == null || !pending.run.equals(reply.run())) return;
        if (reply.action() == IllusionTpNetwork.ABORT) {
            cancel(active, pending, "Client cancelled the transition");
        } else if (reply.action() == IllusionTpNetwork.FINISHED) {
            if (pending.teleported) active.remove(player.getUUID());
        } else if (!pending.teleported) {
            if (player != pending.player || !player.isAlive() || player.serverLevel() != pending.origin) {
                cancel(active, pending, "Player state changed before teleport");
                return;
            }
            try {
                pending.teleport();
            } catch (RuntimeException error) {
                cancel(active, pending, error.getMessage());
                PatchLog.LOGGER.warn("illusionTp cancelled for {}: {}", player.getGameProfile().getName(), error.toString());
            }
        }
    }
    private static void stopMotion(ServerPlayer player) {
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
    }
    private static void cancel(Map<UUID, Pending> active, Pending pending, String reason) {
        active.remove(pending.player.getUUID());
        pending.send(IllusionTpNetwork.CANCEL);
        pending.player.sendSystemMessage(Component.literal("illusionTp cancelled: " + reason));
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent event) { IllusionTpCommands.register(event.getDispatcher()); }
    @SubscribeEvent public void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.player instanceof ServerPlayer player && isActive(player)) stopMotion(player);
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var active = RUNS.get(event.getServer());
        if (active == null) return;
        var iterator = active.entrySet().iterator();
        while (iterator.hasNext()) {
            var slot = iterator.next();
            Pending pending = slot.getValue();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(slot.getKey());
            String reason = player == null ? "Disconnected" : player != pending.player || !player.isAlive()
                    ? "Player died or respawned" : player.serverLevel() != (pending.teleported ? pending.level : pending.origin)
                    ? "Dimension changed" : player.position().distanceToSqr(pending.anchor) > 0.000001
                    ? "Another teleport changed the player's position" : --pending.remaining <= 0 ? "Transition timed out" : null;
            if (reason == null) continue;
            iterator.remove();
            if (player != null) {
                IllusionTpNetwork.send(player, new IllusionTpNetwork.Update(pending.run, IllusionTpNetwork.CANCEL,
                        player.serverLevel().dimension().location(), player.position()));
                player.sendSystemMessage(Component.literal("illusionTp cancelled: " + reason));
            }
        }
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var active = RUNS.get(player.server);
            if (active != null) active.remove(player.getUUID());
        }
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) { RUNS.remove(event.getServer()); }

    private static final class Pending {
        final UUID run = UUID.randomUUID();
        final ServerPlayer player;
        final ServerLevel origin, level;
        final Vec3 destination;
        Vec3 anchor;
        boolean teleported;
        int remaining = TIMEOUT_TICKS;
        Pending(ServerPlayer player, ServerLevel level, Vec3 destination) {
            this.player = player; this.origin = player.serverLevel(); this.level = level;
            this.destination = destination; this.anchor = player.position();
        }
        void send(int action) {
            IllusionTpNetwork.send(player, new IllusionTpNetwork.Update(run, action,
                    (teleported ? level : origin).dimension().location(), anchor));
        }
        void teleport() {
            if (player.server.getLevel(level.dimension()) != level) throw new IllegalStateException("Destination dimension unavailable");
            var event = ForgeEventFactory.onEntityTeleportCommand(player, destination.x, destination.y, destination.z);
            if (event.isCanceled()) throw new IllegalStateException("Teleport was cancelled by another mod");
            Vec3 actual = new Vec3(event.getTargetX(), event.getTargetY(), event.getTargetZ());
            validate(actual);
            // Preserve the current view, including rotations made during the outgoing wave.
            if (!player.teleportTo(level, actual.x, actual.y, actual.z, Set.of(), player.getYRot(), player.getXRot()))
                throw new IllegalStateException("Teleport was rejected");
            // Forge's cross-dimension void overload can cancel travel while the boolean overload still returns true.
            if (player.serverLevel() != level) throw new IllegalStateException("Dimension travel was cancelled");
            anchor = player.position();
            teleported = true;
            remaining = TIMEOUT_TICKS;
            stopMotion(player);
            // Vanilla dimension and position packets precede ARRIVED on this connection.
            send(IllusionTpNetwork.ARRIVED);
        }
    }
}
