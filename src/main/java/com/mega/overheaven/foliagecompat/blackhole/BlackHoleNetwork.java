package com.mega.overheaven.foliagecompat.blackhole;

import com.mega.overheaven.foliagecompat.FoliageCompat;
import com.mega.overheaven.foliagecompat.blackhole.client.BlackHoleClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class BlackHoleNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(FoliageCompat.MOD_ID, "blackhole"), () -> "1", BlackHoleNetwork::compatible, BlackHoleNetwork::compatible);
    private static boolean compatible(String version) { return version.equals("1") || version.equals(NetworkRegistry.ABSENT) || version.equals(NetworkRegistry.ACCEPTVANILLA); }
    public static void init() { CHANNEL.registerMessage(0, Update.class, Update::encode, Update::decode, Update::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT)); }
    public static void snapshot(ServerPlayer player) {
        String dimension = player.level().dimension().location().toString();
        send(player, new Update(0, dimension, "", BlackHoleStore.get(player.server).all().stream().filter(hole -> hole.dimension().equals(dimension)).toList(), player.server.overworld().getGameTime()));
    }
    public static void changed(net.minecraft.server.MinecraftServer server, BlackHole before, BlackHole after) {
        long now = server.overworld().getGameTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String dimension = player.level().dimension().location().toString();
            if (before != null && before.dimension().equals(dimension) && (after == null || !after.dimension().equals(dimension))) send(player, new Update(2, dimension, before.id(), List.of(), now));
            if (after != null && after.dimension().equals(dimension)) send(player, new Update(1, dimension, "", List.of(after), now));
        }
    }
    private static void send(ServerPlayer player, Update update) {
        if (CHANNEL.isRemotePresent(player.connection.connection)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), update);
    }
    public record Update(int operation, String dimension, String removed, List<BlackHole> records, long time) {
        public Update {
            if(time<0 || (operation==2 && !removed.matches("[a-zA-Z0-9_.-]{1,48}")) || records.stream().map(BlackHole::id).distinct().count()!=records.size()) throw new IllegalArgumentException("Invalid update identity/time");
            if (operation < 0 || operation > 2 || records.size() > BlackHoleStore.MAX_RECORDS || (operation == 1 && records.size() != 1) || (operation == 2 && !records.isEmpty())) throw new IllegalArgumentException("Invalid black hole update");
            if (ResourceLocation.tryParse(dimension) == null || records.stream().anyMatch(hole -> !hole.dimension().equals(dimension))) throw new IllegalArgumentException("Invalid update dimension");
            records = List.copyOf(records);
        }
        public void encode(FriendlyByteBuf output) {
            output.writeByte(operation); output.writeUtf(dimension, 256); output.writeUtf(removed, 48); output.writeLong(time); output.writeVarInt(records.size());
            records.forEach(hole -> output.writeNbt(hole.save()));
        }
        public static Update decode(FriendlyByteBuf input) {
            int operation = input.readUnsignedByte(); String dimension = input.readUtf(256); String removed = input.readUtf(48); long time = input.readLong(); int count = input.readVarInt();
            if (count < 0 || count > BlackHoleStore.MAX_RECORDS) throw new IllegalArgumentException("Invalid black hole packet size");
            List<BlackHole> records = new ArrayList<>(count);
            for (int index = 0; index < count; index++) { var tag = input.readNbt(); if (tag == null) throw new IllegalArgumentException("Missing black hole record"); records.add(BlackHole.load(tag)); }
            return new Update(operation, dimension, removed, records, time);
        }
        public void handle(Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BlackHoleClient.accept(this))); context.setPacketHandled(true);
        }
    }
}
