package com.mega.overheaven.foliagecompat.mixin;

import com.github.razorplay01.sway.SwayRenderContext;
import com.github.razorplay01.sway.client.SwayData;
import com.github.razorplay01.sway.client.SwayEngine;
import com.mega.overheaven.foliagecompat.PatchLog;
import com.mega.overheaven.foliagecompat.client.FoliageDiagnostics;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.extensions.IForgeBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/** Supplies missing Forge methods; leaves every existing wrapper and the legacy SWAY method intact. */
@Pseudo
@Mixin(targets = "com.github.razorplay01.sway.platform.forge.util.SwayModel", remap = false)
public abstract class SwayModelMixin implements IForgeBakedModel {
    @Shadow(remap = false) @Final private BakedModel parent;

    @Shadow(remap = false)
    private static BlockPos resolveSwayPos(BlockPos pos, BlockState state) { throw new AssertionError("Mixin shadow"); }

    @Shadow(remap = false)
    private List<BakedQuad> transformQuads(List<BakedQuad> quads, BlockState state, SwayData data, BlockPos pos) {
        throw new AssertionError("Mixin shadow");
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        ModelData result = parent.getModelData(level, pos, state, modelData);
        if (PatchLog.DIAGNOSTICS) FoliageDiagnostics.modelData(state, level, modelData, result);
        return result;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        ChunkRenderTypeSet result = parent.getRenderTypes(state, random, data);
        if (PatchLog.DIAGNOSTICS) FoliageDiagnostics.layers(state, data, result);
        return result;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                    ModelData data, @Nullable RenderType renderType) {
        // Unlike Forge's default method, pass both ModelData and the layer to the parent's Forge overload.
        List<BakedQuad> quads = parent.getQuads(state, side, random, data, renderType);
        if (PatchLog.DIAGNOSTICS) FoliageDiagnostics.quads(state, data, quads);
        if (state == null || quads.isEmpty()) return quads;
        BlockPos pos = SwayRenderContext.getCurrentBlockPos();
        if (pos == null) return quads;
        SwayData sway = SwayEngine.get(resolveSwayPos(pos, state));
        if (sway == null || sway.intensity < 0.01F) return quads;
        // Use SWAY 2.4.5's own interpolation, behavior pipeline and vertex deformation.
        return transformQuads(quads, state, sway, pos);
    }
}
