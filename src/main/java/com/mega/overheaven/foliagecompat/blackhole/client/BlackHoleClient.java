package com.mega.overheaven.foliagecompat.blackhole.client;

import com.mega.overheaven.foliagecompat.FoliageCompat;
import com.mega.overheaven.foliagecompat.blackhole.BlackHole;
import com.mega.overheaven.foliagecompat.blackhole.BlackHoleNetwork;
import com.mega.overheaven.foliagecompat.blackhole.BlackHoleReplica;
import com.mojang.brigadier.arguments.BoolArgumentType;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FoliageCompat.MOD_ID, value = Dist.CLIENT)
public final class BlackHoleClient {
    private static final BlackHoleReplica REPLICA=new BlackHoleReplica();
    public static int steps = 48;
    public static double resolution = 0.5;
    public static boolean diagnostics = Boolean.getBoolean("overheaven.blackhole.diagnostics");
    public static void accept(BlackHoleNetwork.Update update) {
        REPLICA.accept(update);
    }
    public static List<BlackHole> records() {
        var level = Minecraft.getInstance().level;
        return level!=null?REPLICA.records(level.dimension().location().toString()):List.of();
    }
    public static double time(float partial) { return REPLICA.time(Minecraft.getInstance().isPaused()?0:partial); }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && Minecraft.getInstance().level != null && !Minecraft.getInstance().isPaused()) REPLICA.tick();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        REPLICA.clear();
        BlackHoleRenderer.INSTANCE.close();
    }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
        var quality = Commands.literal("quality");
        for (String name : new String[]{"low", "balanced", "high"}) quality.then(Commands.literal(name).executes(context -> {
            steps = name.equals("low") ? 24 : name.equals("high") ? 96 : 48;
            resolution = name.equals("low") ? 0.375 : name.equals("high") ? 0.75 : 0.5;
            context.getSource().sendSuccess(() -> Component.literal("Black hole quality: " + name + " (" + steps + " steps)"), false); return 1;
        }));
        event.getDispatcher().register(Commands.literal("blackholeclient").then(quality)
            .then(Commands.literal("diagnostics").then(Commands.argument("enabled", BoolArgumentType.bool()).executes(context -> {
                diagnostics = BoolArgumentType.getBool(context, "enabled");
                context.getSource().sendSuccess(() -> Component.literal("Black hole diagnostics: " + diagnostics), false); return 1;
            }))));
    }
}
