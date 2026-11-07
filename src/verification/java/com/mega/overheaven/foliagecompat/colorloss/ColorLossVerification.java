package com.mega.overheaven.foliagecompat.colorloss;

import com.mojang.brigadier.CommandDispatcher;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Explicit standalone verification; no server, game, packaging or shared log directory. */
public final class ColorLossVerification {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void near(float a, float b) { check(Math.abs(a - b) < 0.00001f, a + " != " + b); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid input accepted");
    }
    private static ClassNode node(String name) throws Exception {
        try (var stream = ColorLossVerification.class.getClassLoader().getResourceAsStream(name + ".class")) {
            ClassNode result = new ClassNode(); new ClassReader(stream).accept(result, 0); return result;
        }
    }
    public static void main(String[] args) throws Exception {
        var clear = ColorLossProfile.uniform(0); var grey = ColorLossProfile.uniform(1);
        near(ColorLossProfile.channel("R"), 0); near(ColorLossProfile.channel("g"), 1); near(ColorLossProfile.channel("B"), 2);
        near(ColorLossProfile.channel("red"), 0); near(ColorLossProfile.channel("green"), 1); near(ColorLossProfile.channel("BLUE"), 2);
        check(ColorLossProfile.names().equals(java.util.Set.of("R", "G", "B")), "only RGB suggestions");
        for (String removed : new String[]{"orange", "yellow", "cyan", "magenta", "FF0000", "#00ff00", "808080", "bad-value"})
            rejects(() -> ColorLossProfile.channel(removed));
        rejects(() -> ColorLossProfile.uniform(Float.NaN)); rejects(() -> new ColorLossProfile(new float[2]));
        var red = clear.color(0, 1); near(red.at(0), 1); near(red.at(1), 0); near(red.at(2), 0);
        var combined = red.color(1, 1).color(2, 1);
        check(Arrays.equals(combined.values(), grey.values()), "RGB all equals global all");
        var greenOnly = grey.color(1, 0); near(greenOnly.at(1), 0); near(greenOnly.at(0), 1);
        var twoColors = greenOnly.color(0, 0); near(twoColors.at(1), 0); near(twoColors.at(0), 0); near(twoColors.at(2), 1);
        float[] external = red.values(); external[0] = 0; near(red.at(0), 1);
        var transition = new ColorLossTransition(clear, grey, 100, 100);
        near(transition.current(100).at(0), 0); near(transition.current(150).at(0), .5f); near(transition.current(200).at(0), 1);
        near(transition.change(150, clear, 60).current(150).at(0), .5f);
        check(!transition.running(200) && transition.elapsed(999) == 100, "end clamps");
        var store = new ColorLossStore(); UUID id = UUID.randomUUID(); store.global(transition);
        store.player(id, new ColorLossTransition(grey, greenOnly, 125, 80));
        var loaded = ColorLossStore.load(store.save(new CompoundTag()));
        near(loaded.global().current(150).at(0), .5f); near(loaded.forPlayer(id).current(205).at(1), 0);
        check(loaded.overridden(id), "persist override"); loaded.inherit(id); check(!loaded.overridden(id), "inherit");
        near(loaded.forPlayer(id).current(150).at(0), .5f);
        // Legacy 360-degree residuals must be discarded, while clocks and player overrides survive.
        CompoundTag legacy = new CompoundTag(), entry = new CompoundTag(), overrides = new CompoundTag();
        var oldFrom = new net.minecraft.nbt.ListTag(); var oldTo = new net.minecraft.nbt.ListTag();
        for (int h = 0; h < 360; h++) {
            oldFrom.add(net.minecraft.nbt.FloatTag.valueOf(h == 0 ? .1f : h == 120 ? .2f : h == 240 ? .3f : .99f));
            oldTo.add(net.minecraft.nbt.FloatTag.valueOf(h == 0 ? 1 : h == 120 ? .5f : h == 240 ? 0 : .99f));
        }
        entry.put("from", oldFrom); entry.put("to", oldTo); entry.putLong("start", 123); entry.putInt("duration", 60);
        legacy.put("global", entry); overrides.put(id.toString(), entry.copy()); legacy.put("players", overrides);
        var migrated = ColorLossStore.load(legacy);
        check(migrated.overridden(id) && migrated.global().start() == 123 && migrated.global().duration() == 60, "legacy clock and override");
        check(Arrays.equals(migrated.global().from().values(), new float[]{.1f,.2f,.3f}), "legacy start centers");
        check(Arrays.equals(migrated.forPlayer(id).to().values(), new float[]{1,.5f,0}), "legacy target centers only");
        CompoundTag saved = migrated.save(new CompoundTag());
        check(saved.getInt("version") == 2 && saved.getCompound("global").getList("to", 5).size() == 3, "v2 storage");
        var snapshot = new ColorLossNetwork.Snapshot(red, twoColors, 17, 83);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            snapshot.encode(buffer); check(buffer.readableBytes() == 26, "compact v2 packet"); var decoded = ColorLossNetwork.Snapshot.decode(buffer);
            check(Arrays.equals(decoded.from().values(), red.values()) && Arrays.equals(decoded.to().values(), twoColors.values()), "wire RGB precision");
            check(decoded.elapsed() == 17 && decoded.duration() == 83 && buffer.readableBytes() == 0, "wire clock");
        } finally { buffer.release(); }
        var playback = new com.mega.overheaven.foliagecompat.colorloss.client.ColorLossPlayback();
        float[] frameRgb = new float[3];
        playback.accept(new ColorLossNetwork.Snapshot(clear, red, 0, 4));
        playback.sample(0, false, frameRgb); check(playback.consumeHistoryReset(), "first state resets history");
        playback.tick(); playback.sample(.5f, false, frameRgb); check(!playback.consumeHistoryReset(), "normal fade retains TAA");
        float frozen = frameRgb[0]; playback.sample(.9f, true, frameRgb); near(frameRgb[0], frozen);
        playback.tick(); playback.tick(); playback.tick(); playback.sample(0, false, frameRgb);
        near(frameRgb[0], 1); check(playback.consumeHistoryReset(), "endpoint resets once");
        playback.accept(new ColorLossNetwork.Snapshot(clear, red, 4, 4)); playback.sample(0, false, frameRgb);
        check(!playback.consumeHistoryReset(), "periodic endpoint sync does not reset repeatedly");
        playback.accept(new ColorLossNetwork.Snapshot(red, clear, 0, 0)); playback.sample(0, false, frameRgb);
        near(frameRgb[0], 0); check(playback.consumeHistoryReset(), "instant restore resets history");
        playback.invalidateHistory(); check(playback.consumeHistoryReset(), "dimension/reload invalidation");
        playback.clear(); check(!playback.sample(0, false, frameRgb), "logout bypass");
        var dispatcher = new CommandDispatcher<CommandSourceStack>(); ColorLossCommands.register(dispatcher);
        var root = dispatcher.getRoot().getChild("colorLoss");
        var global = root.getChild("global"); var players = root.getChild("players").getChild("targets");
        for (String action : new String[]{"all", "color", "restore", "reset", "hold", "status"})
            check(global.getChild(action) != null && players.getChild(action) != null, "command " + action);
        check(players.getChild("inherit") != null && global.getChild("inherit") == null, "inherit scope");
        check(global.getChild("keep") == null && players.getChild("keep") == null, "keep removed from both scopes");
        for (var scope : java.util.List.of(global, players)) {
            var seconds = scope.getChild("color").getChild("color").getChild("amount").getChild("seconds");
            check(seconds.getCommand() != null && seconds.getChildren().isEmpty(), "fixed RGB range without halfWidth");
        }
        var renderer = node("net/minecraft/client/renderer/GameRenderer");
        var frame = renderer.methods.stream().filter(m -> m.name.equals("render") && m.desc.equals("(FJZ)V")).findFirst().orElseThrow();
        int level = -1, post = -1, gui = -1, count = 0, i = 0;
        for (var instruction : frame.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                if (call.owner.equals(renderer.name) && call.name.equals("renderLevel")
                        && call.desc.equals("(FJLcom/mojang/blaze3d/vertex/PoseStack;)V")) { level = i; count++; }
                if (call.owner.equals("net/minecraft/client/renderer/PostChain") && call.name.equals("process")) post = i;
                if (call.owner.equals("net/minecraft/client/gui/GuiGraphics") && call.name.equals("<init>")) gui = i;
            }
            i++;
        }
        check(count == 1 && level >= 0 && post > level && gui > post, "unique renderLevel return anchor before vanilla post and HUD");
        for (String type : new String[]{"ColorLossProfile", "ColorLossTransition", "ColorLossStore", "ColorLossNetwork", "ColorLossNetwork$Snapshot", "ColorLossCommands", "ColorLossEvents"}) {
            var common = node("com/mega/overheaven/foliagecompat/colorloss/" + type);
            for (var method : common.methods) for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call)
                check(!call.owner.startsWith("net/minecraft/client/") && !call.owner.contains("blackhole/client"), "server isolation " + type);
        }
        var refmap = com.google.gson.JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject().getAsJsonObject("mappings");
        var mappings = refmap.getAsJsonObject("com/mega/overheaven/foliagecompat/colorloss/mixin/GameRendererMixin");
        check(mappings != null && mappings.toString().contains("m_109093_") && mappings.toString().contains("m_109089_"), "production render and renderLevel mappings");
        System.out.println("PASS: RGB state, legacy migration, compact v2 protocol, RGB-only commands, accumulation/recovery, interruption, persistence/inheritance, wire, render anchor and server isolation");
    }
}
