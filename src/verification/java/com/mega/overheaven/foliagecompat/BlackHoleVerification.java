package com.mega.overheaven.foliagecompat;

import com.mega.overheaven.foliagecompat.blackhole.*;
import com.mojang.brigadier.CommandDispatcher;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandSigningContext;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public final class BlackHoleVerification {
    private static int checks;
    private static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); checks++; }
    private static void rejects(Runnable action,String message) { try { action.run(); } catch(IllegalArgumentException exception) { checks++; return; } throw new AssertionError(message); }
    public static void main(String[] arguments) throws Exception {
        BlackHole initial=BlackHole.reference("test","minecraft:overworld",12,70,-20,100,0x123456789abcdefL);
        check(BlackHole.load(initial.save()).equals(initial),"record NBT round trip");
        try(var reader=new java.io.InputStreamReader(BlackHoleVerification.class.getResourceAsStream("/assets/overheaven_foliage_compat/blackhole/presets/reference.json"),java.nio.charset.StandardCharsets.UTF_8)) {
            var preset=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("parameters");
            check(preset.keySet().equals(BlackHole.DEFAULTS.keySet()),"preset schema parity");
            for(var entry:BlackHole.DEFAULTS.entrySet()) {
                var raw=preset.get(entry.getKey()); double value=entry.getKey().endsWith("Color")?Integer.parseInt(raw.getAsString().substring(1),16):entry.getKey().equals("playing")?(raw.getAsBoolean()?1:0):raw.getAsDouble();
                check(value==entry.getValue(),"reference preset parity "+entry.getKey());
            }
        }
        for(String value:new String[]{"NaN","Infinity","-Infinity","-1","129"}) rejects(() -> initial.setting("radius",value,120),"bad numeric radius "+value);
        rejects(() -> initial.setting("inner","1",120),"inner crosses core"); rejects(() -> initial.setting("outer","3.8",120),"outer crosses inner");
        rejects(() -> initial.setting("playing","1",120),"strict boolean"); rejects(() -> initial.setting("hotColor","white",120),"strict RGB");
        rejects(() -> initial.setting("unknown","1",120),"unknown property"); rejects(() -> initial.setting("rotation","4.1",120),"rotation limit");
        rejects(() -> BlackHole.reference("bad id","minecraft:overworld",0,0,0,0,0),"invalid ID");
        rejects(() -> BlackHole.reference("test","bad dimension",0,0,0,0,0),"invalid dimension");
        rejects(() -> BlackHole.reference("test","minecraft:overworld",Double.NaN,0,0,0,0),"non-finite coordinate");
        check(initial.setting("hotColor","#12aBef",120).value("hotColor")==0x12abef,"color parser");
        BlackHole paused=initial.setting("playing","false",140);
        check(paused.time(140)==2 && paused.time(10000)==2,"paused phase stays fixed");
        BlackHole resumed=paused.setting("playing","true",10000);
        check(resumed.time(10000)==2 && resumed.time(10020)==3,"resume continuous");
        check(BlackHole.load(paused.save()).time(10000)==2,"pause survives reload");
        BlackHole moved=initial.move("minecraft:the_nether",30,60,50);
        check(moved.seed()==initial.seed() && moved.phase()==initial.phase() && moved.id().equals(initial.id()),"move preserves identity/animation");
        var corrupt=initial.save(); corrupt.remove("x"); rejects(() -> BlackHole.load(corrupt),"missing numeric coordinate");
        var unknown=initial.save(); unknown.getCompound("settings").putDouble("unknown",1); rejects(() -> BlackHole.load(unknown),"unknown saved field");
        var store=new BlackHoleStore(); store.put(initial); store.put(moved.setting("brightness","4",140).move("minecraft:overworld",30,60,50));
        check(store.all().size()==1,"upsert unique ID");
        var saved=store.save(new CompoundTag()); var loaded=BlackHoleStore.load(saved);
        check(loaded.save(new CompoundTag()).equals(saved),"SavedData round trip");
        var badRoot=new CompoundTag(); badRoot.putInt("schema",1); rejects(() -> BlackHoleStore.load(badRoot),"missing saved list rejected");
        var snapshot=loaded.require("test"); rejects(() -> loaded.put(snapshot.setting("outer","1",150)),"atomic rejected edit");
        check(loaded.require("test").equals(snapshot),"failed edit retained old state");
        for(int index=1;index<BlackHoleStore.MAX_RECORDS;index++) store.put(BlackHole.reference("hole"+index,"minecraft:overworld",0,0,0,100,index));
        rejects(() -> store.put(BlackHole.reference("overflow","minecraft:overworld",0,0,0,100,0)),"record limit");
        for(var update:List.of(new BlackHoleNetwork.Update(0,"minecraft:overworld","",List.copyOf(store.all()),150),new BlackHoleNetwork.Update(1,"minecraft:overworld","",List.of(initial),150),new BlackHoleNetwork.Update(2,"minecraft:overworld","test",List.of(),150))) {
            FriendlyByteBuf buffer=new FriendlyByteBuf(Unpooled.buffer());
            try { update.encode(buffer); check(BlackHoleNetwork.Update.decode(buffer).equals(update),"wire operation "+update.operation()); check(!buffer.isReadable(),"packet fully decoded"); } finally { buffer.release(); }
        }
        rejects(() -> new BlackHoleNetwork.Update(0,"minecraft:the_nether","",List.of(initial),1),"packet wrong dimension");
        rejects(() -> new BlackHoleNetwork.Update(1,"minecraft:overworld","",List.of(),1),"packet missing upsert");
        rejects(() -> new BlackHoleNetwork.Update(2,"minecraft:overworld","",List.of(),1),"packet invalid removal ID");
        rejects(() -> new BlackHoleNetwork.Update(0,"minecraft:overworld","",List.of(initial,initial),1),"packet duplicate ID");
        BlackHoleReplica replica=new BlackHoleReplica(); replica.accept(new BlackHoleNetwork.Update(0,"minecraft:overworld","",List.of(initial),100));
        check(replica.records("minecraft:overworld").equals(List.of(initial)),"login snapshot"); replica.tick(); check(replica.time(0.5f)==101.5,"synchronized time anchor");
        replica.accept(new BlackHoleNetwork.Update(1,"minecraft:overworld","",List.of(paused),140)); check(replica.records("minecraft:overworld").get(0).equals(paused),"update replaces");
        replica.accept(new BlackHoleNetwork.Update(2,"minecraft:overworld","test",List.of(),140)); check(replica.records("minecraft:overworld").isEmpty(),"remove delta");
        replica.accept(new BlackHoleNetwork.Update(0,"minecraft:the_nether","",List.of(moved),150)); check(replica.records("minecraft:overworld").isEmpty(),"no old dimension leakage");
        replica.accept(new BlackHoleNetwork.Update(1,"minecraft:overworld","",List.of(initial),160)); check(replica.records("minecraft:the_nether").equals(List.of(moved)),"ignore mismatched dimension delta");
        replica.clear(); check(replica.records("minecraft:the_nether").isEmpty() && replica.time(0)==0,"disconnect clears cache and clock");
        CommandDispatcher<CommandSourceStack> dispatcher=new CommandDispatcher<>(); BlackHoleCommands.register(dispatcher);
        for(String command:List.of("spawn test 1 2 3","spawn test ~ ~2 ~-1","move test 1 2 3","remove test","info test","list","clear","preset test reference","set test brightness 2","set test hotColor \"#ffffff\"","set test playing false")) {
            var parsed=dispatcher.parse("overheaven blackhole "+command,new ProbeSource(2)); check(!parsed.getReader().canRead() && parsed.getExceptions().isEmpty(),"parse "+command);
        }
        check(dispatcher.parse("overheaven blackhole list",new ProbeSource(0)).getReader().canRead(),"operator permission gate");
        System.out.println("PASS: "+checks+" black hole checks (validation, atomic edits, persistence, packets, dimension replica, animation, command grammar).");
    }
    private static final class ProbeSource extends CommandSourceStack {
        ProbeSource(int permission) { super(CommandSource.NULL,Vec3.ZERO,Vec2.ZERO,null,permission,"test",Component.literal("test"),null,null,true,null,EntityAnchorArgument.Anchor.FEET,CommandSigningContext.ANONYMOUS,null,ignored -> { }); }
    }
}
