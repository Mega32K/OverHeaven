package com.mega.overheaven.foliagecompat.pixeltp;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public final class PixelTpCommands {
    private PixelTpCommands() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pixelTp").requires(source -> source.hasPermission(2))
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("location", Vec3Argument.vec3()).then(timing(false)))
                        .then(Commands.argument("destination", EntityArgument.entity()).then(timing(true)))));
    }
    private static RequiredArgumentBuilder<CommandSourceStack, Double> timing(boolean entity) {
        return Commands.argument("in", DoubleArgumentType.doubleArg(0, 60))
                .then(Commands.argument("duration", DoubleArgumentType.doubleArg(0, 60))
                        .then(Commands.argument("out", DoubleArgumentType.doubleArg(0, 60))
                                .executes(context -> execute(context, entity))));
    }
    private static int execute(CommandContext<CommandSourceStack> context, boolean entity) throws CommandSyntaxException {
        var targets = EntityArgument.getPlayers(context, "targets");
        var source = context.getSource();
        var destination = entity ? EntityArgument.getEntity(context, "destination") : null;
        var level = destination == null ? source.getLevel() : (ServerLevel) destination.level();
        var position = destination == null ? Vec3Argument.getVec3(context, "location") : destination.position();
        var timing = PixelTpTiming.seconds(DoubleArgumentType.getDouble(context, "in"),
                DoubleArgumentType.getDouble(context, "duration"), DoubleArgumentType.getDouble(context, "out"));
        try {
            PixelTpManager.schedule(targets, level, position, destination, timing);
        } catch (IllegalArgumentException | IllegalStateException error) {
            throw new SimpleCommandExceptionType(Component.literal(error.getMessage())).create();
        }
        source.sendSuccess(() -> Component.literal("pixelTp: scheduled " + targets.size() + " player(s); in="
                + timing.in() / 20.0 + "s, duration=" + timing.duration() / 20.0 + "s, out=" + timing.out() / 20.0 + "s"), true);
        return targets.size();
    }
}
