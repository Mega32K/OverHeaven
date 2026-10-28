package com.mega.overheaven.foliagecompat.finale;

import com.mega.overheaven.foliagecompat.FoliageCompat;
import com.mega.overheaven.foliagecompat.blackhole.BlackHole;
import com.mega.overheaven.foliagecompat.blackhole.client.FinaleClient;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

public final class FinaleNetwork {
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(FoliageCompat.MOD_ID,"finale"),()->"1",FinaleNetwork::compatible,FinaleNetwork::compatible);
    private static boolean compatible(String v) { return v.equals("1") || v.equals(NetworkRegistry.ABSENT) || v.equals(NetworkRegistry.ACCEPTVANILLA); }
    public static void init() { CHANNEL.registerMessage(0,Update.class,Update::encode,Update::decode,Update::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT)); }
    public static void send(ServerPlayer player,Update update) { if(CHANNEL.isRemotePresent(player.connection.connection)) CHANNEL.send(PacketDistributor.PLAYER.with(()->player),update); }
    public record Update(String dimension,UUID run,BlackHole hole,FinaleTimeline timeline,int elapsed,boolean paused,boolean preparing,int stopping,long serverTick,long[] glass,long[] debris) {
        public Update {
            if(ResourceLocation.tryParse(dimension)==null || elapsed<0 || stopping< -1 || stopping>40 || serverTick<0 || glass.length>1024 || debris.length>24) throw new IllegalArgumentException("Invalid finale snapshot");
            if(hole!=null && (!hole.dimension().equals(dimension) || elapsed>timeline.total())) throw new IllegalArgumentException("Invalid finale dimension/time");
            glass=glass.clone(); debris=debris.clone();
        }
        public static Update clear(String dimension,long time) { return new Update(dimension,new UUID(0,0),null,FinaleTimeline.defaults(),0,false,false,-1,time,new long[0],new long[0]); }
        public void encode(FriendlyByteBuf out) {
            out.writeUtf(dimension,256); out.writeUUID(run); out.writeBoolean(hole!=null); out.writeLong(serverTick);
            if(hole==null) return;
            out.writeNbt(hole.save()); for(int ticks:timeline.durations()) out.writeVarInt(ticks);
            out.writeVarInt(elapsed); out.writeBoolean(paused); out.writeBoolean(preparing); out.writeInt(stopping); out.writeLongArray(glass); out.writeLongArray(debris);
        }
        public static Update decode(FriendlyByteBuf in) {
            String dimension=in.readUtf(256); UUID run=in.readUUID(); boolean present=in.readBoolean(); long now=in.readLong();
            if(!present) return clear(dimension,now);
            var tag=in.readNbt(); if(tag==null) throw new IllegalArgumentException("Missing finale source"); BlackHole hole=BlackHole.load(tag);
            int[] ticks=new int[8]; for(int i=0;i<8;i++) ticks[i]=in.readVarInt();
            return new Update(dimension,run,hole,new FinaleTimeline(ticks),in.readVarInt(),in.readBoolean(),in.readBoolean(),in.readInt(),now,in.readLongArray(null,1024),in.readLongArray(null,24));
        }
        public void handle(Supplier<NetworkEvent.Context> supplier) { var context=supplier.get(); context.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->FinaleClient.accept(this))); context.setPacketHandled(true); }
    }
}
