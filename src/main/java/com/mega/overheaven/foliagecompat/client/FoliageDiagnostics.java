package com.mega.overheaven.foliagecompat.client;

import com.mega.overheaven.foliagecompat.FoliageCompat;
import com.mega.overheaven.foliagecompat.PatchLog;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/** Read-only, bounded diagnostics; never retains model instances between reloads. */
public final class FoliageDiagnostics {
    private static final int MAX_RELOADS = 4;
    private static final int MAX_RUNTIME_SAMPLES = 64;
    private static volatile boolean pending;
    private static volatile boolean sampling;
    private static volatile int reload;
    private static volatile Set<String> seen = ConcurrentHashMap.newKeySet();
    private static volatile boolean propertyResolved;
    private static ModelProperty<?> gpuProperty;

    private FoliageDiagnostics() { }

    @Mod.EventBusSubscriber(modid = FoliageCompat.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModelEvents {
        @SubscribeEvent
        public static void baked(ModelEvent.BakingCompleted event) {
            if (!PatchLog.DIAGNOSTICS) return;
            if (reload >= MAX_RELOADS) {
                sampling = false;
                pending = false;
                return;
            }
            sampling = true;
            seen = ConcurrentHashMap.newKeySet();
            reload++;
            pending = true;
        }
    }

    @Mod.EventBusSubscriber(modid = FoliageCompat.MOD_ID, value = Dist.CLIENT)
    public static final class ClientEvents {
        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent event) {
            if (!PatchLog.DIAGNOSTICS || !pending || event.phase != TickEvent.Phase.END) return;
            pending = false;
            // ModelManager.apply replaces BlockModelShaper's cache AFTER BakingCompleted.
            // A subsequent client tick observes the cache actually used by chunk builders.
            Minecraft minecraft = Minecraft.getInstance();
            PatchLog.LOGGER.info("[foliage-diag] reload={} installed cache; Forge mod order={}", reload,
                    ModList.get().getMods().stream().map(m -> m.getModId()).filter(id -> id.equals("sway")
                            || id.equals("mc2_interactivefoliage") || id.equals(FoliageCompat.MOD_ID)).toList());
            List<BlockState> states = List.of(Blocks.GRASS.defaultBlockState(), Blocks.FERN.defaultBlockState(),
                    Blocks.DANDELION.defaultBlockState(), Blocks.TALL_GRASS.defaultBlockState(),
                    Blocks.TALL_GRASS.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER),
                    Blocks.SUNFLOWER.defaultBlockState(),
                    Blocks.SUNFLOWER.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER),
                    Blocks.OAK_LEAVES.defaultBlockState());
            for (BlockState state : states) {
                BakedModel model = minecraft.getBlockRenderer().getBlockModelShaper().getBlockModel(state);
                PatchLog.LOGGER.info("[foliage-diag] cached state={} chain={} ModelData-owner={} layers-owner={} quads5-owner={}",
                        state, chain(model), owner(model, "getModelData", BlockAndTintGetter.class, BlockPos.class, BlockState.class, ModelData.class),
                        owner(model, "getRenderTypes", BlockState.class, RandomSource.class, ModelData.class),
                        owner(model, "getQuads", BlockState.class, net.minecraft.core.Direction.class, RandomSource.class, ModelData.class, net.minecraft.client.renderer.RenderType.class));
            }
            if (reload == MAX_RELOADS) PatchLog.LOGGER.info("[foliage-diag] Final cache snapshot budget reached ({} reloads).", MAX_RELOADS);
        }
    }

    public static void modelData(BlockState state, BlockAndTintGetter level, ModelData before, ModelData after) {
        if (sample("data", state, after)) PatchLog.LOGGER.info(
                "[foliage-diag] reload={} ModelData state={} world={} LEFT_TO_GPU={} properties={}->{}",
                reload, state, level.getClass().getName(), marker(after), before.getProperties().size(), after.getProperties().size());
    }

    public static void layers(BlockState state, ModelData data, ChunkRenderTypeSet layers) {
        if (sample("layers", state, data)) PatchLog.LOGGER.info(
                "[foliage-diag] reload={} layers state={} LEFT_TO_GPU={} empty={}", reload, state, marker(data), layers.isEmpty());
    }

    public static void quads(BlockState state, ModelData data, List<BakedQuad> quads) {
        if (sample("quads", state, data)) PatchLog.LOGGER.info(
                "[foliage-diag] reload={} quads5 state={} LEFT_TO_GPU={} count={}", reload, state, marker(data), quads.size());
    }

    private static boolean sample(String phase, BlockState state, ModelData data) {
        if (!PatchLog.DIAGNOSTICS || !sampling || reload == 0 || state == null || seen.size() >= MAX_RUNTIME_SAMPLES) return false;
        Block block = state.getBlock();
        if (block != Blocks.GRASS && block != Blocks.FERN && block != Blocks.DANDELION && block != Blocks.TALL_GRASS
                && block != Blocks.SUNFLOWER && block != Blocks.OAK_LEAVES) return false;
        return seen.add(phase + ":" + BuiltInRegistries.BLOCK.getKey(block) + ":" + marker(data));
    }

    private static String marker(ModelData data) {
        if (!propertyResolved) resolveProperty();
        return gpuProperty == null ? "unavailable" : Boolean.toString(data.has(gpuProperty));
    }

    private static synchronized void resolveProperty() {
        if (propertyResolved) return;
        if (ModList.get().isLoaded("mc2_interactivefoliage")) {
            try {
                Class<?> model = Class.forName("net.karto.mc2.mc2_interactivefoliage.platform.forge.ForgeFoliageModel", false, FoliageDiagnostics.class.getClassLoader());
                Field property = model.getDeclaredField("LEFT_TO_GPU");
                property.setAccessible(true);
                gpuProperty = (ModelProperty<?>) property.get(null);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                PatchLog.LOGGER.warn("[foliage-diag] Cannot inspect LEFT_TO_GPU; marker will be reported as unavailable.", exception);
            }
        }
        propertyResolved = true;
    }

    private static String owner(BakedModel model, String method, Class<?>... args) {
        try {
            return model.getClass().getMethod(method, args).getDeclaringClass().getName();
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return "unavailable:" + exception.getClass().getSimpleName();
        }
    }

    private static String chain(BakedModel first) {
        StringBuilder result = new StringBuilder();
        Set<BakedModel> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        BakedModel model = first;
        for (int depth = 0; model != null && depth < 12; depth++) {
            if (!visited.add(model)) return result.append(" -> [cycle]").toString();
            if (!result.isEmpty()) result.append(" -> ");
            result.append(model.getClass().getName());
            try {
                Field parent;
                if (model.getClass().getName().equals("com.github.razorplay01.sway.platform.forge.util.SwayModel")) {
                    parent = model.getClass().getDeclaredField("parent");
                } else if (model instanceof BakedModelWrapper<?>) {
                    parent = BakedModelWrapper.class.getDeclaredField("originalModel");
                } else {
                    return result.append(" [leaf or opaque]").toString();
                }
                parent.setAccessible(true);
                model = (BakedModel) parent.get(model);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                return result.append(" [unreadable parent]").toString();
            }
        }
        return result.append(model == null ? "" : " -> [depth limit]").toString();
    }
}
