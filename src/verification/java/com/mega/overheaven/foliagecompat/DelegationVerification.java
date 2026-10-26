package com.mega.overheaven.foliagecompat;

import com.mega.overheaven.foliagecompat.mixin.SwayModelMixin;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;

/** Exercises method bodies only; this does not claim to test runtime Mixin application or animation. */
public final class DelegationVerification {
    private static final ModelProperty<Boolean> ORIGINAL = new ModelProperty<>();
    private static final ModelProperty<Boolean> GPU = new ModelProperty<>();
    private static final class Probe extends SwayModelMixin { }
    private static final class Parent implements BakedModel {
        ModelData expected;
        ModelData marked;
        List<BakedQuad> quads = Collections.emptyList();
        int dataCalls;
        int layerCalls;
        int quadCalls;
        @Override public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
            if (data != expected) throw new AssertionError("original ModelData must be forwarded by identity");
            dataCalls++;
            marked = data.derive().with(GPU, true).build();
            return marked;
        }
        @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
            if (data != marked) throw new AssertionError("parent must receive GPU ModelData on the layer path");
            layerCalls++;
            return null; // No RenderType static initialization or graphics context is needed.
        }
        @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType layer) {
            if (data != marked || layer != null) throw new AssertionError("data and null breaking/item layer must survive");
            quadCalls++;
            return quads;
        }
        @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
            throw new AssertionError("the three-argument default fallback loses Forge ModelData");
        }
        @Override public boolean useAmbientOcclusion() { return true; }
        @Override public boolean isGui3d() { return false; }
        @Override public boolean usesBlockLight() { return false; }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleIcon() { return null; }
        @Override public ItemTransforms getTransforms() { return null; }
        @Override public ItemOverrides getOverrides() { return null; }
    }
    public static void main(String[] args) throws Exception {
        if (PatchLog.DIAGNOSTICS) throw new AssertionError("run headless verification with diagnostics off");
        Probe probe = new Probe();
        Parent parent = new Parent();
        var field = SwayModelMixin.class.getDeclaredField("parent");
        field.setAccessible(true);
        field.set(probe, parent);
        parent.expected = ModelData.builder().with(ORIGINAL, true).build();
        ModelData marked = probe.getModelData(null, null, null, parent.expected);
        if (marked != parent.marked || !marked.has(ORIGINAL) || !marked.has(GPU)) throw new AssertionError("lost or replaced ModelData");
        probe.getRenderTypes(null, null, marked);
        if (probe.getQuads(null, null, null, marked, null) != parent.quads) throw new AssertionError("empty parent geometry must remain empty");
        parent.quads = Collections.singletonList(null); // Sentinel geometry; no model/texture/GL initialization.
        if (probe.getQuads(null, null, null, marked, null) != parent.quads) throw new AssertionError("non-GPU geometry must remain available");
        if (parent.dataCalls != 1 || parent.layerCalls != 1 || parent.quadCalls != 2) throw new AssertionError("duplicate delegation");
        System.out.println("PASS: ModelData identity/properties, layer data, GPU empty geometry, fallback geometry and single delegation.");
    }
}
