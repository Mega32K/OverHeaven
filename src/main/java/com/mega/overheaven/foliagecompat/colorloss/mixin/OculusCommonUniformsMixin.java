package com.mega.overheaven.foliagecompat.colorloss.mixin;

import com.mega.overheaven.foliagecompat.blackhole.client.ColorLossRenderer;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.shaderpack.IdMap;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.CommonUniforms", remap = false)
public abstract class OculusCommonUniformsMixin {
    @Inject(method = "addNonDynamicUniforms(Lnet/irisshaders/iris/gl/uniform/UniformHolder;Lnet/irisshaders/iris/shaderpack/IdMap;Lnet/irisshaders/iris/shaderpack/properties/PackDirectives;Lnet/irisshaders/iris/uniforms/FrameUpdateNotifier;)V", at = @At("TAIL"))
    private static void overheaven$uniforms(UniformHolder holder, IdMap ids, PackDirectives directives, FrameUpdateNotifier notifier, CallbackInfo ci) {
        holder.uniform3f(UniformUpdateFrequency.PER_FRAME, "ohColorLossResidual", ColorLossRenderer.INSTANCE::residual);
        holder.uniform1b(UniformUpdateFrequency.PER_FRAME, "ohColorLossResetHistory", ColorLossRenderer.INSTANCE::resetHistory);
        com.mega.overheaven.foliagecompat.worldwarp.client.OculusWarpBridge.uniforms(holder);
    }
}
