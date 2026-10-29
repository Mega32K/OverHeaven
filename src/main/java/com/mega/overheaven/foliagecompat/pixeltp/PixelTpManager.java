package com.mega.overheaven.foliagecompat.pixeltp;

import com.mega.overheaven.foliagecompat.PatchLog;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Server-thread-owned destinations. Client replies carry only a nonce, never coordinates. */
public final class PixelTpManager {
    private static final Map<MinecraftServer, Map<UUID, Pending>> RUNS = new WeakHashMap<>();
    private static final int NETWORK_GRACE = 600;
    private static Map<UUID, Pending> runs(MinecraftServer server) {
        return RUNS.computeIfAbsent(server, ignored -> new HashMap<>());
    }
    public static boolean isActive(ServerPlayer player) {
        var active = RUNS.get(player.server);
        return active != null && active.containsKey(player.getUUID());
    }
    private static void validate(Vec3 position) {
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)
                || !Level.isInSpawnableBounds(BlockPos.containing(position)))
            throw new IllegalArgumentException("pixelTp: destination is outside the world's coordinate limits");
    }
    public static void schedule(Collection<ServerPlayer> players, ServerLevel level, Vec3 position,
                                Entity destination, PixelTpTiming timing) {
        validate(position);
        var active = runs(level.getServer());
        // Validate the whole selector before any player receives an effect.
        for (ServerPlayer player : players) {
            if (!player.isAlive()) throw new IllegalStateException("pixelTp: target player is not alive");
            if (!PixelTpNetwork.available(player))
                throw new IllegalStateException("pixelTp: " + player.getGameProfile().getName() + " needs this mod's pixelTp client support");
            if (active.containsKey(player.getUUID()) || com.mega.overheaven.foliagecompat.illusiontp.IllusionTpManager.isActive(player))
                throw new IllegalStateException("pixelTp: " + player.getGameProfile().getName() + " already has an active transition");
        }
        for (ServerPlayer player : players) {
            float yaw = destination == null ? player.getYRot() : destination.getYRot();
            float pitch = destination == null ? player.getXRot() : destination.getXRot();
            Pending pending = new Pending(player, level, position, yaw, pitch, timing);
            active.put(player.getUUID(), pending);
            pending.send(player, PixelTpNetwork.BEGIN, BlockPos.containing(position));
        }
    }
    public static void reply(ServerPlayer player, PixelTpNetwork.Reply reply) {
        var active = RUNS.get(player.server);
        if (active == null) return;
        Pending pending = active.get(player.getUUID());
        if (pending == null || !pending.run.equals(reply.run())) return;
        if (reply.action() == PixelTpNetwork.ABORT) {
            cancel(active, player, pending, "Client cancelled the transition");
        } else if (reply.action() == PixelTpNetwork.FINISHED) {
            if (pending.teleported) active.remove(player.getUUID());
        } else if (!pending.teleported) {
            if (!player.isAlive() || player.serverLevel() != pending.origin || player != pending.player) {
                cancel(active, player, pending, "Player state changed before teleport");
                return;
            }
            try {
                pending.teleport(player);
            } catch (RuntimeException error) {
                cancel(active, player, pending, error.getMessage());
                PatchLog.LOGGER.warn("pixelTp cancelled for {}: {}", player.getGameProfile().getName(), error.toString());
            }
        }
    }
    private static void cancel(Map<UUID, Pending> active, ServerPlayer player, Pending pending, String reason) {
        active.remove(player.getUUID());
        pending.send(player, PixelTpNetwork.CANCEL, player.blockPosition());
        player.sendSystemMessage(Component.literal("pixelTp cancelled: " + reason));
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent event) { PixelTpCommands.register(event.getDispatcher()); }
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
                    ? "Dimension changed" : --pending.remaining <= 0 ? "Transition timed out" : null;
            if (reason == null) continue;
            iterator.remove();
            if (player != null) {
                pending.send(player, PixelTpNetwork.CANCEL, player.blockPosition());
                player.sendSystemMessage(Component.literal("pixelTp cancelled: " + reason));
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
        final Vec3 position;
        final float yaw, pitch;
        final PixelTpTiming timing;
        boolean teleported;
        int remaining;
        Pending(ServerPlayer player, ServerLevel level, Vec3 position, float yaw, float pitch, PixelTpTiming timing) {
            this.player = player; this.origin = player.serverLevel(); this.level = level;
            this.position = position; this.yaw = yaw; this.pitch = pitch; this.timing = timing;
            remaining = timing.in() + NETWORK_GRACE;
        }
        void send(ServerPlayer player, int action, BlockPos destination) {
            PixelTpNetwork.send(player, new PixelTpNetwork.Update(run, action, timing, level.dimension().location(), destination));
        }
        void teleport(ServerPlayer player) {
            if (player.server.getLevel(level.dimension()) != level) throw new IllegalStateException("Destination dimension unavailable");
            // Match Forge's vanilla teleport-command event, including cancellation and adjusted coordinates.
            var event = ForgeEventFactory.onEntityTeleportCommand(player, position.x, position.y, position.z);
            if (event.isCanceled()) throw new IllegalStateException("Teleport was cancelled by another mod");
            Vec3 actual = new Vec3(event.getTargetX(), event.getTargetY(), event.getTargetZ());
            validate(actual);
            if (!player.teleportTo(level, actual.x, actual.y, actual.z, Set.of(), yaw, pitch))
                throw new IllegalStateException("Teleport was rejected");
            if (!player.isFallFlying()) {
                player.setDeltaMovement(player.getDeltaMovement().multiply(1, 0, 1));
                player.setOnGround(true);
            }
            player.fallDistance = 0;
            teleported = true;
            remaining = timing.duration() + timing.out() + NETWORK_GRACE;
            // Vanilla position/dimension packets have already been queued on this connection.
            send(player, PixelTpNetwork.ARRIVED, player.blockPosition());
        }
    }
}
