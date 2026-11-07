package com.mega.overheaven.foliagecompat.worldwarp;

import com.mega.overheaven.foliagecompat.FoliageCompat;
import java.util.Optional;
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

public final class WorldWarpNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(FoliageCompat.MOD_ID, "world_warp"), () -> "2",
            WorldWarpNetwork::compatible, WorldWarpNetwork::compatible);
    private static Consumer<Update> receiver = update -> {};
    private WorldWarpNetwork() {}
    private static boolean compatible(String value) {
        return value.equals("2") || value.equals(NetworkRegistry.ABSENT) || value.equals(NetworkRegistry.ACCEPTVANILLA);
    }
    public static void init() {
        CHANNEL.registerMessage(0, Update.class, Update::encode, Update::decode, Update::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void receiver(Consumer<Update> value) { receiver = value; }
    public static boolean available(ServerPlayer player) { return CHANNEL.isRemotePresent(player.connection.connection); }
    public static void send(ServerPlayer player, WorldWarpState state, long tick) {
        if (available(player)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Update(state, tick));
    }
    private static void vec(FriendlyByteBuf out, Vec3 v) { out.writeDouble(v.x); out.writeDouble(v.y); out.writeDouble(v.z); }
    private static Vec3 vec(FriendlyByteBuf in) { return new Vec3(in.readDouble(), in.readDouble(), in.readDouble()); }
    public record Update(WorldWarpState state, long tick) {
        void encode(FriendlyByteBuf out) {
            out.writeLong(tick); out.writeBoolean(state != null);
            if (state == null) return;
            out.writeResourceLocation(state.dimension()); vec(out, state.center()); vec(out, state.axis());
            out.writeBoolean(state.followEyes());
            out.writeDouble(state.strength()); out.writeDouble(state.periodTicks()); out.writeLong(state.startTick());
            out.writeLong(state.fadeStart()); out.writeDouble(state.fadeTicks());
        }
        static Update decode(FriendlyByteBuf in) {
            long tick = in.readLong();
            WorldWarpState state = in.readBoolean() ? new WorldWarpState(in.readResourceLocation(), vec(in), vec(in),
                    in.readBoolean(), in.readDouble(), in.readDouble(), in.readLong(), in.readLong(), in.readDouble()) : null;
            return new Update(state, tick);
        }
        void handle(Supplier<NetworkEvent.Context> source) {
            var context = source.get();
            context.enqueueWork(() -> receiver.accept(this));
            context.setPacketHandled(true);
        }
    }
}
