package com.mega.overheaven.foliagecompat;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import com.mega.overheaven.foliagecompat.cards.PlayingCards;

@Mod(FoliageCompat.MOD_ID)
public final class FoliageCompat {
    public static final String MOD_ID = "overheaven_foliage_compat";

    public FoliageCompat(FMLJavaModLoadingContext context) {
        PlayingCards.register(context.getModEventBus());
        com.mega.overheaven.foliagecompat.blackhole.BlackHoleNetwork.init();
        com.mega.overheaven.foliagecompat.finale.FinaleNetwork.init();
        com.mega.overheaven.foliagecompat.pixeltp.PixelTpNetwork.init();
        com.mega.overheaven.foliagecompat.illusiontp.IllusionTpNetwork.init();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new com.mega.overheaven.foliagecompat.illusiontp.IllusionTpManager());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new com.mega.overheaven.foliagecompat.pixeltp.PixelTpManager());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new com.mega.overheaven.foliagecompat.finale.FinaleEvents());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new com.mega.overheaven.foliagecompat.blackhole.BlackHoleEvents());
    }
}
