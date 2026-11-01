package com.mega.overheaven.foliagecompat.shelf.client;

import com.mega.overheaven.foliagecompat.shelf.ShelfBlock;
import com.mega.overheaven.foliagecompat.shelf.ShelfBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;

public final class ShelfRenderer implements BlockEntityRenderer<ShelfBlockEntity> {
    private final ItemRenderer items;

    public ShelfRenderer(BlockEntityRendererProvider.Context context) { items = context.getItemRenderer(); }

    @Override public void render(ShelfBlockEntity shelf, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var facing = shelf.getBlockState().getValue(ShelfBlock.FACING);
        for (int slot = 0; slot < 3; slot++) {
            var stack = shelf.displayedItem(slot);
            if (stack.isEmpty()) continue;
            pose.pushPose();
            try {
                pose.translate(0.5, 0.5, 0.5);
                pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
                pose.translate((slot - 1) * 0.3125, 0, -0.25);
                pose.scale(0.25F, 0.25F, 0.25F);
                items.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose, buffers, shelf.getLevel(),
                        shelf.getBlockPos().hashCode() + slot);
            } finally {
                pose.popPose();
            }
        }
    }
}
