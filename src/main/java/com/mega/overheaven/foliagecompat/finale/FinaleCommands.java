package com.mega.overheaven.foliagecompat.finale;

import com.mega.overheaven.foliagecompat.blackhole.BlackHoleStore;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.*;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;

public final class FinaleCommands {
    public static LiteralArgumentBuilder<CommandSourceStack> create() {
        var root=Commands.literal("finale");
        for(String op:new String[]{"play","pause","resume","stop","status","reset"}) root.then(Commands.literal(op).then(id().executes(c->execute(c,op))));
        root.then(Commands.literal("duration").then(id().then(Commands.argument("phase",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(java.util.Arrays.stream(FinaleTimeline.Phase.values()).map(FinaleTimeline.Phase::id),b)).then(Commands.argument("seconds",DoubleArgumentType.doubleArg(1,600)).executes(c->execute(c,"duration"))))));
        return root;
    }
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack,String> id() { return Commands.argument("id",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(BlackHoleStore.get(c.getSource().getServer()).all().stream().map(h->h.id()),b)); }
    private static int execute(CommandContext<CommandSourceStack> c,String op) throws CommandSyntaxException {
        try {
            var server=c.getSource().getServer(); String id=StringArgumentType.getString(c,"id"); var hole=op.equals("status")?null:BlackHoleStore.get(server).require(id); var manager=FinaleManager.get(server); var store=FinaleStore.get(server);
            switch(op) {
                case "play" -> manager.play(hole);
                case "pause" -> manager.pause(id,true);
                case "resume" -> manager.pause(id,false);
                case "stop" -> manager.stop(id);
                case "duration","reset" -> { if(manager.active(id)) throw new IllegalArgumentException("Stop the finale before changing phase durations"); if(op.equals("reset")) store.reset(id); else store.put(id,store.get(id).with(FinaleTimeline.Phase.parse(StringArgumentType.getString(c,"phase")),DoubleArgumentType.getDouble(c,"seconds"))); }
                default -> { }
            }
            String message="Finale "+id+" "+op+": "+manager.status(id)+"; phase ticks="+java.util.Arrays.toString(store.get(id).durations());
            c.getSource().sendSuccess(()->Component.literal(message),!op.equals("status")); return 1;
        } catch(IllegalArgumentException|IllegalStateException error) { throw new SimpleCommandExceptionType(Component.literal(error.getMessage())).create(); }
    }
}
