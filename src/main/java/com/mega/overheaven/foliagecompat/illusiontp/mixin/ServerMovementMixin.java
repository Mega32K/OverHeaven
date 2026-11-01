package com.mega.overheaven.foliagecompat.illusiontp.mixin;

import com.mega.overheaven.foliagecompat.illusiontp.IllusionTpManager;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerMovementMixin {
    @Shadow public ServerPlayer player;
    @Shadow private boolean clientIsFloating;

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void overheaven$illusionFloating(CallbackInfo callback) {
        if (IllusionTpManager.isActive(player)) clientIsFloating = false;
    }

    // Run after the vanilla thread hand-off; retain vanilla rotation validation and teleport acknowledgements.
    @ModifyVariable(method = "handleMovePlayer(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), argsOnly = true)
    private ServerboundMovePlayerPacket overheaven$illusionPosition(ServerboundMovePlayerPacket packet) {
        if (!IllusionTpManager.isActive(player)) return packet;
        return new ServerboundMovePlayerPacket.Rot(packet.getYRot(player.getYRot()), packet.getXRot(player.getXRot()), player.onGround());
    }
}
