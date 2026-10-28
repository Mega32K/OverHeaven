package com.mega.overheaven.foliagecompat;

import com.mega.overheaven.foliagecompat.finale.*;
import com.mega.overheaven.foliagecompat.blackhole.BlackHole;
import io.netty.buffer.Unpooled;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class FinaleVerification {
    private static int checks;
    private static void check(boolean value,String text) { if(!value) throw new AssertionError(text); checks++; }
    private static void rejects(Runnable action) { try { action.run(); } catch(IllegalArgumentException expected) { checks++; return; } throw new AssertionError("Invalid input accepted"); }
    public static void main(String[] args) throws Exception {
        var timeline=FinaleTimeline.defaults(); check(timeline.total()==2400,"120 seconds");
        int elapsed=0;
        for(var phase:FinaleTimeline.Phase.values()) {
            check(timeline.start(phase)==elapsed,"phase ordering"); check(timeline.frame(elapsed).phase()==phase,"inclusive start");
            check(timeline.frame(elapsed+timeline.duration(phase)*.8).progress()>.7999,"normalized cues"); elapsed+=timeline.duration(phase);
        }
        check(timeline.frame(2400).progress()==1,"ending clamps");
        check(Math.abs(timeline.impactTicks()-808)<0.0001,"fracture impact at 40.4 seconds");
        check(timeline.flash(807.99)==0 && timeline.flash(808)==0 && timeline.flash(813)==.0625 && timeline.flash(818)==1 && timeline.flash(820)==1 && timeline.flash(822)==.0625 && timeline.flash(824)==0,"quartic rise/hold/fall endpoints");
        check(timeline.takeover(817)<0.0001 && timeline.takeover(818)>=.999,"cosmos starts beneath white");
        check(timeline.takeover(830)>=.999,"cosmos remains after flash");
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,0,600.01}) rejects(()->timeline.with(FinaleTimeline.Phase.EXPOSE,bad));
        for(var phase:FinaleTimeline.Phase.values()) {
            var changed=timeline.with(phase,1.25); check(changed.duration(phase)==25,"20Hz duration quantization"); check(timeline.duration(phase)>=80,"immutable definition");
        }
        var store=new FinaleStore(); store.put("alpha",timeline.with(FinaleTimeline.Phase.SILENCE,12));
        var loaded=FinaleStore.load(store.save(new CompoundTag())); check(Arrays.equals(loaded.get("alpha").durations(),store.get("alpha").durations()),"definition save roundtrip");
        loaded.reset("alpha"); check(loaded.get("alpha").total()==2400,"reset defaults");
        for(int tick=2260;tick<2320;tick++) { var f=timeline.frame(tick); check(FinaleTimeline.black(f),"last three seconds black"); check(FinaleTimeline.gameGain(f)==0,"last three seconds muted"); }
        check(FinaleTimeline.gameGain(timeline.frame(2400))==1,"sound restored");
        for(int tick=0;tick<=2400;tick++) { double coverage=timeline.takeover(tick); check(coverage>=0 && coverage<=1,"bounded cover"); }
        for(double duration:new double[]{1,1.25,6,18,600}) {
            var changed=timeline.with(FinaleTimeline.Phase.FRACTURE,duration);
            double trigger=changed.impactTicks();
            check(trigger==changed.start(FinaleTimeline.Phase.FRACTURE)+changed.duration(FinaleTimeline.Phase.FRACTURE)*.8,"duration moves trigger");
            for(double offset:new double[]{-1,0,5,10,12,14,16,17}) check(Math.abs(changed.flash(trigger+offset)-timeline.flash(808+offset))<1e-10,"fixed flash duration under phase edits");
            check(changed.takeover(trigger+9.99)==0 && changed.takeover(trigger+10)==1,"background switches at peak under phase edits");
            if(duration==1) check(changed.frame(trigger+16).phase()==FinaleTimeline.Phase.REVERSE,"flash crosses phase boundary continuously");
        }
        for(int elapsedTick:new int[]{807,813,818,819,822,824,2208,2288}) {
            var flashClock=new FinaleClock(timeline);
            for(int cursor=0;cursor<elapsedTick;cursor++) flashClock.tick();
            flashClock.pause(true); var frozen=timeline.exposure(flashClock.elapsed(),-1);
            for(int cursor=0;cursor<90;cursor++) flashClock.tick();
            check(frozen.equals(timeline.exposure(flashClock.elapsed(),-1)),"pause freezes flash and coverage");
            flashClock.pause(false); flashClock.tick(); check(flashClock.elapsed()==elapsedTick+1,"resume advances one tick");
            flashClock.stop(); var stopped=timeline.exposure(flashClock.elapsed(),0);
            double previousWhite=stopped.white(),previousCoverage=stopped.coverage();
            for(int cursor=0;cursor<=40;cursor++) {
                var envelope=timeline.exposure(flashClock.elapsed(),flashClock.stopping()/40.0);
                check(envelope.white()<=previousWhite && envelope.coverage()<=previousCoverage,"stop never replays or increases takeover");
                check(envelope.equals(timeline.exposure(elapsedTick+1,cursor/40.0)),"latejoin reconstructs stop from existing wire fields");
                previousWhite=envelope.white(); previousCoverage=envelope.coverage(); flashClock.tick();
            }
            check(previousWhite==0 && previousCoverage==0,"stop returns both layers fully");
        }
        var clock=new FinaleClock(timeline); for(int i=0;i<500;i++) clock.tick(); clock.pause(true);
        for(int i=0;i<500;i++) clock.tick(); check(clock.elapsed()==500,"pause freezes server time"); clock.pause(false); clock.tick(); check(clock.elapsed()==501,"resume continuous");
        clock.pause(true); clock.stop(); for(int i=0;i<39;i++) clock.tick(); check(!clock.finished() && !clock.paused(),"stop transitions while paused"); clock.stop(); clock.tick(); check(clock.finished(),"repeated stop cannot restart return");
        rejects(()->clock.pause(true)); var second=new FinaleClock(timeline); check(second.elapsed()==0 && !second.finished(),"new independent run/restart starts clean");
        var hole=BlackHole.reference("alpha","minecraft:overworld",0,70,0,0,3999);
        long[] positions={4,9,16}; var packet=new FinaleNetwork.Update(hole.dimension(),UUID.randomUUID(),hole,timeline,800,true,false,-1,500,positions,new long[]{12}); positions[0]=999;
        check(packet.glass()[0]==4,"input arrays detached");
        var buffer=new FriendlyByteBuf(Unpooled.buffer()); packet.encode(buffer); var decoded=FinaleNetwork.Update.decode(buffer); buffer.release();
        check(decoded.run().equals(packet.run()) && decoded.hole().equals(hole) && decoded.paused() && decoded.elapsed()==800 && Arrays.equals(decoded.glass(),packet.glass()),"latejoin full snapshot roundtrip");
        var clear=FinaleNetwork.Update.clear("minecraft:the_nether",1000); buffer=new FriendlyByteBuf(Unpooled.buffer()); clear.encode(buffer); check(FinaleNetwork.Update.decode(buffer).hole()==null,"dimension clear"); buffer.release();
        rejects(()->new FinaleNetwork.Update("minecraft:the_nether",packet.run(),hole,timeline,0,false,false,-1,1,new long[0],new long[0]));
        rejects(()->new FinaleNetwork.Update(hole.dimension(),packet.run(),hole,timeline,0,false,false,-1,1,new long[1025],new long[0]));
        var dispatcher=new com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>(); dispatcher.register(FinaleCommands.create());
        var root=dispatcher.getRoot().getChild("finale"); for(String command:new String[]{"play","pause","resume","stop","status","duration","reset"}) check(root.getChild(command)!=null,"command tree "+command);
        ClassNode renderer=node("net/minecraft/client/renderer/GameRenderer.class"); MethodNode render=renderer.methods.stream().filter(m->m.name.equals("render")&&m.desc.equals("(FJZ)V")).findFirst().orElseThrow();
        int order=0,world=-1,post=-1,clearIndex=-1;
        for(var instruction:render.instructions) { if(instruction instanceof MethodInsnNode call) { if(call.name.equals("renderLevel")) world=order; if(call.owner.equals("net/minecraft/client/renderer/PostChain")&&call.name.equals("process")) post=order; if(call.owner.equals("com/mojang/blaze3d/systems/RenderSystem")&&call.name.equals("clear")&&clearIndex<0) clearIndex=order; } order++; }
        check(world>=0 && post>world && clearIndex>post,"late compositor after world/post and before GUI clear");
        ClassNode chunk=node("net/minecraft/world/level/chunk/LevelChunk.class"); check(chunk.methods.stream().anyMatch(m->m.name.equals("setBlockState")&&m.desc.equals("(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;")),"exact mutation gate descriptor");
        ClassNode sound=node("net/minecraft/client/sounds/SoundEngine.class"); check(sound.methods.stream().anyMatch(m->m.name.equals("calculateVolume")&&m.desc.equals("(Lnet/minecraft/client/resources/sounds/SoundInstance;)F")),"sound volume descriptor");
        for(String name:new String[]{"FinaleTimeline","FinaleStore","FinaleManager","FinaleJournal","FinaleCommands","FinaleEvents"}) {
            ClassNode common=node("com/mega/overheaven/foliagecompat/finale/"+name+".class");
            for(MethodNode method:common.methods) for(var i:method.instructions) if(i instanceof MethodInsnNode call) check(!call.owner.startsWith("net/minecraft/client/")&&!call.owner.contains("blackhole/client"),"common side isolation "+name);
        }
        var refmap=com.google.gson.JsonParser.parseString(Files.readString(Path.of("build/tmp/compileJava/overheaven_foliage_compat.refmap.json"))).getAsJsonObject().getAsJsonObject("mappings");
        for(String mixin:new String[]{"FinaleRendererMixin","FinaleSoundMixin","GlassReservationMixin"}) check(refmap.has("com/mega/overheaven/foliagecompat/finale/mixin/"+mixin),"generated production refmap "+mixin);
        var config=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/overheaven_foliage_compat.finale.mixins.json"))).getAsJsonObject();
        check(config.getAsJsonArray("mixins").size()==1 && config.getAsJsonArray("client").size()==3,"client mixin distribution boundary");
        var listener=node("net/minecraft/client/multiplayer/ClientPacketListener.class");
        check(listener.methods.stream().anyMatch(m->m.name.equals("handleMovePlayer") && m.desc.equals("(Lnet/minecraft/network/protocol/game/ClientboundPlayerPositionPacket;)V")),"teleport packet target descriptor exists");
        check(listener.methods.stream().anyMatch(m->m.name.equals("handleSetCamera") && m.desc.equals("(Lnet/minecraft/network/protocol/game/ClientboundSetCameraPacket;)V")),"camera-switch packet target descriptor exists");
        var reset=node("com/mega/overheaven/foliagecompat/finale/mixin/FinaleCameraResetMixin.class");
        check(reset.methods.stream().anyMatch(m->m.name.equals("overheaven$resetCrossing") && m.desc.equals("(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V")),"client-only camera reset callback descriptor");
        var mix=node("com/mega/overheaven/foliagecompat/finale/mixin/FinaleSoundMixin.class");
        check(mix.fields.stream().anyMatch(f->f.name.equals("instanceToChannel")&&f.desc.equals("Ljava/util/Map;")),"sound channel field descriptor");
        var finale=node("com/mega/overheaven/foliagecompat/blackhole/client/FinaleRenderer.class");
        var renderLayers=finale.methods.stream().filter(method->method.name.equals("render")).findFirst().orElseThrow();
        int callIndex=0,skyIndex=-1,starsIndex=-1,subjectIndex=-1,composeIndex=-1;
        for(var instruction:renderLayers.instructions) {
            if(instruction instanceof MethodInsnNode call) {
                if(call.owner.endsWith("/FinaleStarMap") && call.name.equals("drawBackground")) skyIndex=callIndex;
                if(call.owner.endsWith("/FinaleStarMap") && call.name.equals("drawStars")) starsIndex=callIndex;
                if(call.name.equals("renderCosmos")) subjectIndex=callIndex;
                if(call.owner.endsWith("/GlProgram") && call.name.equals("texture")) composeIndex=callIndex;
                check(!call.name.equals("finaleDepth"),"late composition never imports terrain depth");
                callIndex++;
            }
        }
        check(skyIndex>=0 && skyIndex<starsIndex && starsIndex<subjectIndex && subjectIndex<composeIndex,"production order is sky then subjects then late composition");
        for(var instruction:renderLayers.instructions) if(instruction instanceof MethodInsnNode call)
            check(!(call.owner.endsWith("/OptionalShaders") && (call.name.equals("skySource") || call.name.equals("skyIdentity") || call.name.equals("active"))),"cosmic sky source independent of shader-pack toggle");
        var mainRenderer=node("com/mega/overheaven/foliagecompat/blackhole/client/BlackHoleRenderer.class");
        var begin=mainRenderer.methods.stream().filter(method->method.name.equals("begin")).findFirst().orElseThrow();
        boolean skipWorld=false;
        for(var instruction:begin.instructions) if(instruction instanceof MethodInsnNode call && call.name.equals("skipWorld")) skipWorld=true;
        check(skipWorld,"complete takeover gates ordinary world solver");
        var cosmic=mainRenderer.methods.stream().filter(method->method.name.equals("renderCosmos")).findFirst().orElseThrow();
        boolean unit=false,far=false;
        for(var instruction:cosmic.instructions) if(instruction instanceof FieldInsnNode field) { unit|=field.name.equals("UNIT_TRANSMISSION"); far|=field.name.equals("FAR_DEPTH"); }
        check(unit && far,"cosmic path supplies empty depth and unit transmission");
        var shaderRoot=Path.of("src/main/resources/assets/overheaven_foliage_compat/shaders/blackhole");
        for(String name:new String[]{"finale_stars.fsh","finale_nasa.fsh","finale_star_points.fsh"}) {
            String shader=Files.readString(shaderRoot.resolve(name));
            check(!shader.contains("finaleRemnant") && !shader.contains("finale_remnant"),"background contains no duplicate subject "+name);
        }
        var release=finale.methods.stream().filter(method->method.name.equals("close")).findFirst().orElseThrow();
        boolean releasesSubjects=false,releasesCosmos=false;
        for(var instruction:release.instructions) if(instruction instanceof FieldInsnNode field) { releasesSubjects|=field.name.equals("subjects"); releasesCosmos|=field.name.equals("cosmicScene"); }
        check(releasesSubjects && releasesCosmos,"finale lifetime owns cosmic solver and target");
        var client=node("com/mega/overheaven/foliagecompat/blackhole/client/FinaleClient.class");
        var camera=client.methods.stream().filter(method->method.name.equals("camera")).findFirst().orElseThrow();
        check(camera.desc.equals("(Lnet/minecraftforge/client/event/ViewportEvent$ComputeCameraAngles;)V"),"verified Forge camera event descriptor");
        int angleWrites=0;
        for(var instruction:camera.instructions) if(instruction instanceof MethodInsnNode call) {
            if(call.owner.equals("net/minecraftforge/client/event/ViewportEvent$ComputeCameraAngles") && java.util.Set.of("setYaw","setPitch","setRoll").contains(call.name)) angleWrites++;
            check(!java.util.Set.of("setYRot","setXRot","setPos","teleportTo").contains(call.name),"camera impulse never mutates player transform");
        }
        check(angleWrites==3,"three additive camera event angles");
        for(String type:new String[]{"FinaleWaves","FinaleWaveFeedback"}) {
            var common=node("com/mega/overheaven/foliagecompat/finale/"+type+".class");
            for(var method:common.methods) for(var instruction:method.instructions) if(instruction instanceof MethodInsnNode call) check(!call.owner.startsWith("net/minecraft/client"),"wave clock remains client-class independent");
        }
        System.out.println("Finale timeline, NBT, wire, commands and bytecode contracts: "+checks+" checks passed");
    }
    private static ClassNode node(String path) throws Exception { try(var in=FinaleVerification.class.getClassLoader().getResourceAsStream(path)) { if(in==null) throw new AssertionError(path); ClassNode n=new ClassNode(); new ClassReader(in).accept(n,0); return n; } }
}
