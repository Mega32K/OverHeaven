package com.mega.overheaven.foliagecompat.pixeltp;

import com.mega.overheaven.foliagecompat.FoliageCompat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class PixelTpNetwork {
    public static final int BEGIN = 0, ARRIVED = 1, CANCEL = 2;
    public static final int COVERED = 0, FINISHED = 1, ABORT = 2;
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(FoliageCompat.MOD_ID, "pixel_tp"), () -> "1",
            PixelTpNetwork::compatible, PixelTpNetwork::compatible);
    // Installed during client setup. Common packet registration has no client class linkage.
    private static Consumer<Update> clientReceiver = update -> {};
    private static boolean compatible(String version) {
        return version.equals("1") || version.equals(NetworkRegistry.ABSENT) || version.equals(NetworkRegistry.ACCEPTVANILLA);
    }
    public static void init() {
        CHANNEL.registerMessage(0, Update.class, Update::encode, Update::decode, Update::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, Reply.class, Reply::encode, Reply::decode, Reply::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
    public static void clientReceiver(Consumer<Update> receiver) { clientReceiver = receiver; }
    public static boolean available(ServerPlayer player) { return CHANNEL.isRemotePresent(player.connection.connection); }
    public static void send(ServerPlayer player, Update update) {
        if (available(player)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), update);
    }
    public static void reply(UUID run, int action) { CHANNEL.sendToServer(new Reply(run, action)); }

    public record Update(UUID run, int action, PixelTpTiming timing, ResourceLocation dimension, BlockPos destination) {
        public Update {
            if (run == null || timing == null || dimension == null || destination == null || action < 0 || action > CANCEL)
                throw new IllegalArgumentException("Invalid pixelTp update");
            destination = destination.immutable();
        }
        void encode(FriendlyByteBuf buf) {
            buf.writeUUID(run); buf.writeVarInt(action);
            buf.writeVarInt(timing.in()); buf.writeVarInt(timing.duration()); buf.writeVarInt(timing.out());
            buf.writeResourceLocation(dimension); buf.writeBlockPos(destination);
        }
        static Update decode(FriendlyByteBuf buf) {
            return new Update(buf.readUUID(), buf.readVarInt(), new PixelTpTiming(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()),
                    buf.readResourceLocation(), buf.readBlockPos());
        }
        void handle(Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get();
            context.enqueueWork(() -> clientReceiver.accept(this));
            context.setPacketHandled(true);
        }
    }
    public record Reply(UUID run, int action) {
        public Reply {
            if (run == null || action < 0 || action > ABORT) throw new IllegalArgumentException("Invalid pixelTp reply");
        }
        void encode(FriendlyByteBuf buf) { buf.writeUUID(run); buf.writeVarInt(action); }
        static Reply decode(FriendlyByteBuf buf) { return new Reply(buf.readUUID(), buf.readVarInt()); }
        void handle(Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null) PixelTpManager.reply(player, this);
            });
            context.setPacketHandled(true);
        }
    }
}
