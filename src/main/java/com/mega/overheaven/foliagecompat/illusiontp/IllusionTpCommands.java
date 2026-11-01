package com.mega.overheaven.foliagecompat.illusiontp;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public final class IllusionTpCommands {
    private IllusionTpCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("illusionTp").requires(source -> source.hasPermission(2))
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("location", Vec3Argument.vec3()).executes(context -> execute(context, false, false))
                                .then(Commands.literal("in")
                                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                                .executes(context -> execute(context, false, true)))))
                        .then(Commands.argument("destination", EntityArgument.entity()).executes(context -> execute(context, true, false)))));
    }

    private static int execute(CommandContext<CommandSourceStack> context, boolean entity, boolean explicitDimension) throws CommandSyntaxException {
        var targets = EntityArgument.getPlayers(context, "targets");
        var source = context.getSource();
        var destination = entity ? EntityArgument.getEntity(context, "destination") : null;
        var level = explicitDimension ? DimensionArgument.getDimension(context, "dimension")
                : destination == null ? source.getLevel() : (ServerLevel) destination.level();
        var position = destination == null ? Vec3Argument.getVec3(context, "location") : destination.position();
        try {
            IllusionTpManager.schedule(targets, level, position);
        } catch (IllegalArgumentException | IllegalStateException error) {
            throw new SimpleCommandExceptionType(Component.literal(error.getMessage())).create();
        }
        source.sendSuccess(() -> Component.literal("illusionTp: scheduled " + targets.size() + " player(s)"), true);
        return targets.size();
    }
}
