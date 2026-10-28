package com.mega.overheaven.foliagecompat.finale.mixin;

import com.mega.overheaven.foliagecompat.blackhole.client.FinaleClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class FinaleCameraResetMixin {
    // RETURN executes after vanilla's main-thread packet dispatch and camera/player relocation.
    // These exact 1.20.1 descriptors were checked against the mapped Forge 47.4.10 artifact.
    @Inject(method={"handleMovePlayer(Lnet/minecraft/network/protocol/game/ClientboundPlayerPositionPacket;)V",
                    "handleSetCamera(Lnet/minecraft/network/protocol/game/ClientboundSetCameraPacket;)V"},at=@At("RETURN"))
    private void overheaven$resetCrossing(CallbackInfo callback) { FinaleClient.resetFeedback(); }
}
