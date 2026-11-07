package com.mega.overheaven.foliagecompat.worldwarp;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public final class WorldWarpCommands {
    private WorldWarpCommands() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("worldWarp").requires(source -> source.hasPermission(2))
                .then(Commands.argument("targets", EntityArgument.players())
                    .then(Commands.literal("start")
                        .then(Commands.argument("center", Vec3Argument.vec3(false))
                            .then(rotation(false))))
                    .then(Commands.literal("followEyes").then(rotation(true)))
                    .then(Commands.literal("stop").executes(c -> stop(c, 1))
                        .then(Commands.argument("fadeSeconds", DoubleArgumentType.doubleArg(0))
                            .executes(c -> stop(c, DoubleArgumentType.getDouble(c, "fadeSeconds")))))));
    }
    private static ArgumentBuilder<CommandSourceStack, ?> rotation(boolean followEyes) {
        return Commands.argument("rotation", RotationArgument.rotation())
                .executes(c -> start(c, followEyes, 1, 25))
                .then(Commands.argument("strength", DoubleArgumentType.doubleArg(0))
                    .executes(c -> start(c, followEyes, DoubleArgumentType.getDouble(c, "strength"), 25))
                    .then(Commands.argument("periodSeconds", DoubleArgumentType.doubleArg(0.05))
                        .executes(c -> start(c, followEyes, DoubleArgumentType.getDouble(c, "strength"),
                                DoubleArgumentType.getDouble(c, "periodSeconds")))));
    }
    private static int start(CommandContext<CommandSourceStack> c, boolean followEyes, double strength, double period) throws CommandSyntaxException {
        var source = c.getSource();
        var rotation = RotationArgument.getRotation(c, "rotation").getRotation(source);
        long tick = source.getServer().overworld().getGameTime();
        var state = new WorldWarpState(source.getLevel().dimension().location(),
                followEyes ? Vec3.ZERO : Vec3Argument.getVec3(c, "center"),
                WorldWarpState.direction(rotation.y, rotation.x), followEyes, strength, period * 20, tick, -1, 0);
        int count = WorldWarpEvents.start(EntityArgument.getPlayers(c, "targets"), state, tick);
        source.sendSuccess(() -> Component.literal("worldWarp: " + (followEyes ? "following each player's eyes for " : "started for ")
                + count + " player(s) in this dimension"), true);
        return count;
    }
    private static int stop(CommandContext<CommandSourceStack> c, double fade) throws CommandSyntaxException {
        int count = WorldWarpEvents.stop(EntityArgument.getPlayers(c, "targets"),
                c.getSource().getServer().overworld().getGameTime(), fade * 20);
        c.getSource().sendSuccess(() -> Component.literal("worldWarp: stopping " + count + " player(s)"), true);
        return count;
    }
}
