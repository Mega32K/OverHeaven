package com.mega.overheaven.foliagecompat.blackhole.client;

import com.mega.overheaven.foliagecompat.colorloss.client.LayeredColorLossPack;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;

/** Optional Oculus references stay on the physical client. No framebuffer ownership here. */
public final class OculusColorLossBridge {
    private OculusColorLossBridge() {}
    public static boolean layered() {
        return IrisApi.getInstance().isShaderPackInUse()
                && Iris.getCurrentPack().orElse(null) instanceof LayeredColorLossPack pack
                && pack.overheaven$layeredColorLoss();
    }
}
