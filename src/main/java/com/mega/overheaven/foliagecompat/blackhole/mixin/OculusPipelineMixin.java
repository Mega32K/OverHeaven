package com.mega.overheaven.foliagecompat.blackhole.mixin;

import com.google.common.collect.ImmutableSet;
import com.mega.overheaven.foliagecompat.blackhole.client.OculusBridge;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="net.irisshaders.iris.pipeline.IrisRenderingPipeline",remap=false)
public abstract class OculusPipelineMixin {
    @Shadow @Final private RenderTargets renderTargets;
    @Shadow public abstract ImmutableSet<Integer> getFlippedAfterTranslucent();
    @Inject(method="beginTranslucents()V",at=@At("TAIL"))
    private void overheaven$opaque(CallbackInfo callback) { OculusBridge.before(renderTargets,getFlippedAfterTranslucent()); }
    @Inject(method="finalizeLevelRendering()V",at=@At("HEAD"))
    private void overheaven$compose(CallbackInfo callback) { OculusBridge.after(renderTargets,getFlippedAfterTranslucent()); }
}
