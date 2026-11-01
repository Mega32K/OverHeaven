package com.mega.overheaven.foliagecompat.illusiontp;

import com.mega.overheaven.foliagecompat.FoliageCompat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class IllusionTpNetwork {
    public static final int BEGIN = 0, ARRIVED = 1, CANCEL = 2;
    public static final int COVERED = 0, FINISHED = 1, ABORT = 2;
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(FoliageCompat.MOD_ID, "illusion_tp"), () -> "1",
            IllusionTpNetwork::compatible, IllusionTpNetwork::compatible);
    // Installed on the physical client; registering this channel never links client classes.
    private static Consumer<Update> clientReceiver = update -> {};

    private IllusionTpNetwork() {}
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

    public record Update(UUID run, int action, ResourceLocation dimension, Vec3 anchor) {
        public Update {
            if (run == null || dimension == null || anchor == null || action < BEGIN || action > CANCEL
                    || !Double.isFinite(anchor.x) || !Double.isFinite(anchor.y) || !Double.isFinite(anchor.z))
                throw new IllegalArgumentException("Invalid illusionTp update");
        }
        void encode(FriendlyByteBuf buf) {
            buf.writeUUID(run); buf.writeVarInt(action); buf.writeResourceLocation(dimension);
            buf.writeDouble(anchor.x); buf.writeDouble(anchor.y); buf.writeDouble(anchor.z);
        }
        static Update decode(FriendlyByteBuf buf) {
            return new Update(buf.readUUID(), buf.readVarInt(), buf.readResourceLocation(),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
        }
        void handle(Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get();
            context.enqueueWork(() -> clientReceiver.accept(this));
            context.setPacketHandled(true);
        }
    }

    public record Reply(UUID run, int action) {
        public Reply {
            if (run == null || action < COVERED || action > ABORT) throw new IllegalArgumentException("Invalid illusionTp reply");
        }
        void encode(FriendlyByteBuf buf) { buf.writeUUID(run); buf.writeVarInt(action); }
        static Reply decode(FriendlyByteBuf buf) { return new Reply(buf.readUUID(), buf.readVarInt()); }
        void handle(Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null) IllusionTpManager.reply(player, this);
            });
            context.setPacketHandled(true);
        }
    }
}
