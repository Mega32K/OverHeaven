package com.mega.overheaven.foliagecompat.blackhole.mixin;

import com.mega.overheaven.foliagecompat.blackhole.client.OculusBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="net.irisshaders.iris.gl.program.Program",remap=false)
public abstract class OculusProgramMixin {
    @Shadow public abstract int getProgramId();
    @Inject(method="destroyInternal()V",at=@At("HEAD"))
    private void overheaven$deleted(CallbackInfo callback) { OculusBridge.destroyed(getProgramId()); }
}
