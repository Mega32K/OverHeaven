package com.mega.overheaven.foliagecompat.colorloss;

import com.mega.overheaven.foliagecompat.FoliageCompat;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ColorLossNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(FoliageCompat.MOD_ID, "color_loss"), () -> "2", ColorLossNetwork::compatible, ColorLossNetwork::compatible);
    // Physical-client setup installs the receiver; dedicated-server registration never links renderer classes.
    private static Consumer<Snapshot> receiver = snapshot -> {};
    private ColorLossNetwork() {}
    private static boolean compatible(String version) {
        return version.equals("2") || version.equals(NetworkRegistry.ABSENT) || version.equals(NetworkRegistry.ACCEPTVANILLA);
    }
    public static void init() {
        CHANNEL.registerMessage(0, Snapshot.class, Snapshot::encode, Snapshot::decode, Snapshot::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void receiver(Consumer<Snapshot> client) { receiver = client; }
    public static boolean available(ServerPlayer player) { return CHANNEL.isRemotePresent(player.connection.connection); }
    public static void sync(ServerPlayer player) {
        if (!available(player)) return;
        var state = ColorLossStore.get(player.server).forPlayer(player.getUUID());
        int elapsed = state.elapsed(player.server.overworld().getGameTime());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Snapshot(state.from(), state.to(), elapsed, state.duration()));
    }
    public record Snapshot(ColorLossProfile from, ColorLossProfile to, int elapsed, int duration) {
        public Snapshot {
            if (elapsed < 0 || elapsed > duration || duration > ColorLossTransition.MAX_DURATION)
                throw new IllegalArgumentException("Invalid color-loss clock");
        }
        void encode(FriendlyByteBuf buf) {
            for (float value : from.values()) buf.writeFloat(value);
            for (float value : to.values()) buf.writeFloat(value);
            buf.writeVarInt(elapsed); buf.writeVarInt(duration);
        }
        static Snapshot decode(FriendlyByteBuf buf) {
            float[] from = new float[ColorLossProfile.SAMPLES], to = new float[ColorLossProfile.SAMPLES];
            for (int i = 0; i < from.length; i++) from[i] = buf.readFloat();
            for (int i = 0; i < to.length; i++) to[i] = buf.readFloat();
            return new Snapshot(new ColorLossProfile(from), new ColorLossProfile(to), buf.readVarInt(), buf.readVarInt());
        }
        void handle(Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get(); context.enqueueWork(() -> receiver.accept(this)); context.setPacketHandled(true);
        }
    }
}
