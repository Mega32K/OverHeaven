package com.mega.overheaven.foliagecompat.worldwarp.mixin;

import com.mega.overheaven.foliagecompat.worldwarp.client.*;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import java.util.Collection;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
public abstract class EmbeddiumManagerMixin {
    @Shadow @Final private Long2ReferenceMap<?> sectionByPosition;
    @Unique private boolean overheaven$lastActive;
    @Unique private long overheaven$sortEpoch = -1;
    @Unique private Collection<?> overheaven$uploaded;
    @Inject(method = "shouldUseOcclusionCulling(Lnet/minecraft/client/Camera;Z)Z", at = @At("HEAD"), cancellable = true)
    private void overheaven$warpOcclusion(Camera camera, boolean spectator, CallbackInfoReturnable<Boolean> ci) {
        if (WorldWarpClient.worldPass()) ci.setReturnValue(false);
    }
    @Inject(method = "needsUpdate()Z", at = @At("HEAD"), cancellable = true)
    private void overheaven$warpUpdate(CallbackInfoReturnable<Boolean> ci) {
        if (OculusWarpBridge.shadows()) return;
        boolean active = WorldWarpClient.worldPass();
        if (active || active != overheaven$lastActive) ci.setReturnValue(true);
        overheaven$lastActive = active;
    }
    @Inject(method = "checkTranslucencyChange()V", at = @At("HEAD"))
    private void overheaven$warpSort(CallbackInfo ci) {
        if (OculusWarpBridge.shadows()) return;
        var frame = WorldWarpClient.frame();
        if (!frame.enabled() && overheaven$sortEpoch == frame.epoch()) return;
        overheaven$sortEpoch = frame.epoch();
        for (Object section : sectionByPosition.values())
            ((WarpSection)section).overheaven$invalidateWarpSort(frame);
    }
    @ModifyArg(method = "processChunkBuildResults(Ljava/util/ArrayList;)V",
            at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/chunk/region/RenderRegionManager;uploadMeshes(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;Ljava/util/Collection;)V"), index = 1)
    private Collection<?> overheaven$accepted(Collection<?> results) {
        overheaven$uploaded = results;
        return results;
    }
    @Inject(method = "processChunkBuildResults(Ljava/util/ArrayList;)V", at = @At("RETURN"))
    private void overheaven$uploaded(CallbackInfo ci) {
        for (Object result : overheaven$uploaded) ((WarpBuildOutput)result).overheaven$uploaded();
        overheaven$uploaded = null;
    }
}
