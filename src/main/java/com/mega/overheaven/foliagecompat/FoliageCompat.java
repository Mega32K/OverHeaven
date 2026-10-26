package com.mega.overheaven.foliagecompat;

import net.minecraftforge.fml.common.Mod;

@Mod(FoliageCompat.MOD_ID)
public final class FoliageCompat {
    public static final String MOD_ID = "overheaven_foliage_compat";

    public FoliageCompat() {
        com.mega.overheaven.foliagecompat.blackhole.BlackHoleNetwork.init();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new com.mega.overheaven.foliagecompat.blackhole.BlackHoleEvents());
    }
}
