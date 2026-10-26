package com.mega.overheaven.foliagecompat.blackhole.mixin;

import com.mega.overheaven.foliagecompat.blackhole.client.OculusBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="net.irisshaders.iris.pathways.FullScreenQuadRenderer",remap=false)
public abstract class OculusQuadMixin {
    @Inject(method="renderQuad()V",at=@At("TAIL"))
    private void overheaven$front(CallbackInfo callback) { OculusBridge.quad(); }
}
