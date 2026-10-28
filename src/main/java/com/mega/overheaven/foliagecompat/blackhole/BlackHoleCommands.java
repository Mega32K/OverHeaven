package com.mega.overheaven.foliagecompat.blackhole;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;

public final class BlackHoleCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("blackhole");
        root.then(com.mega.overheaven.foliagecompat.finale.FinaleCommands.create());
        for (String operation : new String[]{"spawn", "move"}) root.then(Commands.literal(operation).then(id().then(Commands.argument("position", Vec3Argument.vec3(false)).executes(context -> execute(context, operation)))));
        for (String operation : new String[]{"remove", "info"}) root.then(Commands.literal(operation).then(id().executes(context -> execute(context, operation))));
        root.then(Commands.literal("list").executes(context -> execute(context, "list")));
        root.then(Commands.literal("clear").executes(context -> execute(context, "clear")));
        root.then(Commands.literal("preset").then(id().then(Commands.literal("reference").executes(context -> execute(context, "preset")))));
        root.then(Commands.literal("set").then(id().then(Commands.argument("property", StringArgumentType.word()).suggests((context, builder) -> SharedSuggestionProvider.suggest(BlackHole.DEFAULTS.keySet(), builder)).then(Commands.argument("value", StringArgumentType.string()).executes(context -> execute(context, "set"))))));
        dispatcher.register(Commands.literal("overheaven").requires(source -> source.hasPermission(2)).then(root));
    }
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> id() {
        return Commands.argument("id", StringArgumentType.word()).suggests((context, builder) -> SharedSuggestionProvider.suggest(BlackHoleStore.get(context.getSource().getServer()).all().stream().map(BlackHole::id), builder));
    }
    private static int execute(CommandContext<CommandSourceStack> context, String operation) throws CommandSyntaxException {
        var source = context.getSource(); var server = source.getServer(); var store = BlackHoleStore.get(server);
        String dimension = source.getLevel().dimension().location().toString(); long now = server.overworld().getGameTime();
        try {
            if (operation.equals("list") || operation.equals("clear")) {
                var records = store.all().stream().filter(hole -> hole.dimension().equals(dimension)).toList();
                if (operation.equals("clear")) for (var hole : records) { com.mega.overheaven.foliagecompat.finale.FinaleManager.get(server).beforeSourceChange(hole.id()); store.remove(hole.id()); BlackHoleNetwork.changed(server, hole, null); }
                String message = operation + ": " + records.size() + " [" + String.join(", ", records.stream().map(BlackHole::id).toList()) + "]";
                source.sendSuccess(() -> Component.literal(message), operation.equals("clear")); return records.size();
            }
            String id = StringArgumentType.getString(context, "id"); BlackHole before = null; BlackHole after = null;
            if (operation.equals("spawn")) {
                if (store.all().stream().anyMatch(hole -> hole.id().equals(id))) throw new IllegalArgumentException("ID already exists: " + id);
                var position = Vec3Argument.getVec3(context, "position");
                after = BlackHole.reference(id, dimension, position.x, position.y, position.z, now, server.overworld().getRandom().nextLong());
            } else {
                before = store.require(id);
                if (operation.equals("info")) { String message = id + " @ " + before.dimension() + " " + before.x() + " " + before.y() + " " + before.z() + " " + before.settings(); source.sendSuccess(() -> Component.literal(message), false); return 1; }
                if (operation.equals("move")) { var position = Vec3Argument.getVec3(context, "position"); after = before.move(dimension, position.x, position.y, position.z); }
                if (operation.equals("preset")) after = BlackHole.reference(id, before.dimension(), before.x(), before.y(), before.z(), now, before.seed());
                if (operation.equals("set")) after = before.setting(StringArgumentType.getString(context, "property"), StringArgumentType.getString(context, "value"), now);
            }
            if (after == null || operation.equals("move")) com.mega.overheaven.foliagecompat.finale.FinaleManager.get(server).beforeSourceChange(id);
            if (after == null) store.remove(id); else store.put(after);
            BlackHoleNetwork.changed(server, before, after); source.sendSuccess(() -> Component.literal(operation + ": " + id), true); return 1;
        } catch (IllegalArgumentException exception) { throw new SimpleCommandExceptionType(Component.literal(exception.getMessage())).create(); }
    }
}
