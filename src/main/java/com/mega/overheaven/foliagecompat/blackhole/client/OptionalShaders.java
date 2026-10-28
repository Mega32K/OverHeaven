package com.mega.overheaven.foliagecompat.blackhole.client;

import net.minecraftforge.fml.ModList;

final class OptionalShaders {
    private static final boolean OCULUS=ModList.get().isLoaded("oculus");
    private static final boolean SUPPORTED=!OCULUS || ModList.get().getModContainerById("oculus").orElseThrow().getModInfo().getVersion().toString().equals("1.8.0");
    static boolean active() { return OCULUS && (!SUPPORTED || OculusBridge.active()); }
    static boolean shadow() { return OCULUS && SUPPORTED && OculusBridge.shadow(); }
    static Object skyIdentity() { return OCULUS && SUPPORTED?OculusBridge.skyIdentity():null; }
    static String skySource() throws java.io.IOException {
        if(!OCULUS || !SUPPORTED) throw new java.io.IOException("Finale pack sky requires Oculus 1.8.0");
        return OculusBridge.skySource();
    }
    static void completed() { if(OCULUS && SUPPORTED) OculusBridge.completed(); }
    static void reset() { if(OCULUS && SUPPORTED) OculusBridge.resetSession(); }
}
