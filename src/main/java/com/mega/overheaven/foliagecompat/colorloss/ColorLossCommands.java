package com.mega.overheaven.foliagecompat.colorloss;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ColorLossCommands {
    private enum Operation { ALL, COLOR, RESTORE, HOLD, RESET }
    private ColorLossCommands() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("colorLoss").requires(source -> source.hasPermission(2))
                .then(actions(Commands.literal("global"), true))
                .then(Commands.literal("players").then(actions(Commands.argument("targets", EntityArgument.players()), false)
                        .then(Commands.literal("inherit").executes(ColorLossCommands::inherit)))));
    }
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T actions(T node, boolean global) {
        node.then(Commands.literal("all").then(timed(Commands.argument("amount", FloatArgumentType.floatArg(0, 1)), global, Operation.ALL)));
        node.then(Commands.literal("color").then(color().then(timed(Commands.argument("amount", FloatArgumentType.floatArg(0, 1)), global, Operation.COLOR))));
        node.then(timed(Commands.literal("restore"), global, Operation.RESTORE));
        node.then(Commands.literal("hold").executes(context -> edit(context, global, Operation.HOLD, false)));
        node.then(Commands.literal("reset").executes(context -> edit(context, global, Operation.RESET, false)));
        node.then(Commands.literal("status").executes(context -> status(context, global)));
        return node;
    }
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> color() {
        return Commands.argument("color", StringArgumentType.string())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(ColorLossProfile.names(), builder));
    }
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T timed(T node, boolean global, Operation op) {
        node.executes(context -> edit(context, global, op, false));
        var seconds = Commands.argument("seconds", DoubleArgumentType.doubleArg(0, 3600))
                .executes(context -> edit(context, global, op, true));
        node.then(seconds); return node;
    }
    private static int edit(CommandContext<CommandSourceStack> context, boolean global, Operation op, boolean timed) throws CommandSyntaxException {
        var source = context.getSource(); var server = source.getServer(); var store = ColorLossStore.get(server);
        long now = server.overworld().getGameTime();
        double seconds = timed ? DoubleArgumentType.getDouble(context, "seconds") : 3;
        int ticks = op == Operation.HOLD || op == Operation.RESET ? 0 : (int) Math.round(seconds * 20);
        float amount = op == Operation.ALL || op == Operation.COLOR ? FloatArgumentType.getFloat(context, "amount") : 0;
        int channel = 0;
        if (op == Operation.COLOR) {
            try { channel = ColorLossProfile.channel(StringArgumentType.getString(context, "color")); }
            catch (IllegalArgumentException error) { throw new SimpleCommandExceptionType(Component.literal(error.getMessage())).create(); }
        }
        List<ServerPlayer> targets = global ? server.getPlayerList().getPlayers() : List.copyOf(EntityArgument.getPlayers(context, "targets"));
        if (!global) for (var player : targets) if (!ColorLossNetwork.available(player))
            throw new SimpleCommandExceptionType(Component.literal(player.getScoreboardName() + " 未安装失色系统客户端")).create();
        if (global) store.global(changed(store.global(), now, op, channel, amount, ticks));
        else for (var player : targets) store.player(player.getUUID(), changed(store.forPlayer(player.getUUID()), now, op, channel, amount, ticks));
        int notified = 0;
        for (var player : targets) if (!global || !store.overridden(player.getUUID())) {
            ColorLossNetwork.sync(player); if (ColorLossNetwork.available(player)) notified++;
        }
        final int count = notified;
        source.sendSuccess(() -> Component.literal("失色设置已更新：" + (global ? "全局默认" : "玩家覆盖")
                + "，渐变 " + ticks / 20.0 + " 秒，同步 " + count + " 名玩家"), true);
        return global ? 1 : targets.size();
    }
    private static ColorLossTransition changed(ColorLossTransition state, long now, Operation op, int channel, float amount, int ticks) {
        ColorLossProfile current = state.current(now);
        ColorLossProfile target = switch (op) {
            case ALL -> ColorLossProfile.uniform(amount);
            // Build on the existing destination so consecutive color commands accumulate.
            case COLOR -> state.to().color(channel, amount);
            case RESTORE, RESET -> ColorLossProfile.uniform(0);
            case HOLD -> current;
        };
        return new ColorLossTransition(current, target, now, ticks);
    }
    private static int inherit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var players = EntityArgument.getPlayers(context, "targets"); var store = ColorLossStore.get(context.getSource().getServer());
        for (var player : players) { store.inherit(player.getUUID()); ColorLossNetwork.sync(player); }
        context.getSource().sendSuccess(() -> Component.literal("已清除 " + players.size() + " 名玩家的失色覆盖，恢复跟随全局"), true);
        return players.size();
    }
    private static int status(CommandContext<CommandSourceStack> context, boolean global) throws CommandSyntaxException {
        var source = context.getSource(); var store = ColorLossStore.get(source.getServer()); long now = source.getServer().overworld().getGameTime();
        if (global) describe(source, "全局默认", store.global(), now);
        else for (var player : EntityArgument.getPlayers(context, "targets"))
            describe(source, player.getScoreboardName() + (store.overridden(player.getUUID()) ? "（单独覆盖）" : "（跟随全局）"), store.forPlayer(player.getUUID()), now);
        return 1;
    }
    private static void describe(CommandSourceStack source, String label, ColorLossTransition transition, long now) {
        var current = transition.current(now);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "%s：当前 RGB=(%.2f, %.2f, %.2f)，目标=(%.2f, %.2f, %.2f)，剩余 %.2f 秒",
                label, current.at(0), current.at(1), current.at(2), transition.to().at(0), transition.to().at(1), transition.to().at(2), (transition.duration() - transition.elapsed(now)) / 20.0)), false);
    }
}
