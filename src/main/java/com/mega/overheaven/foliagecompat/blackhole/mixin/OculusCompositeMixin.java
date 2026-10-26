package com.mega.overheaven.foliagecompat.blackhole.mixin;

import com.google.common.collect.ImmutableSet;
import com.mega.overheaven.foliagecompat.blackhole.client.OculusBridge;
import java.util.function.Supplier;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="net.irisshaders.iris.pipeline.CompositeRenderer",remap=false)
public abstract class OculusCompositeMixin {
    @Inject(method="createProgram(Lnet/irisshaders/iris/shaderpack/programs/ProgramSource;Lcom/google/common/collect/ImmutableSet;Lcom/google/common/collect/ImmutableSet;Ljava/util/function/Supplier;)Lnet/irisshaders/iris/gl/program/Program;",at=@At("RETURN"))
    private void overheaven$program(ProgramSource source,ImmutableSet<Integer> flipped,ImmutableSet<Integer> once,Supplier<ShadowRenderTargets> shadows,CallbackInfoReturnable<Program> callback) {
        OculusBridge.tag(source.getName(),callback.getReturnValue().getProgramId());
    }
}
