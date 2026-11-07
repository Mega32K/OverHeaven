package com.mega.overheaven.foliagecompat.worldwarp;

import java.util.List;
import java.util.Set;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class WorldWarpMixinPlugin implements IMixinConfigPlugin {
    public static boolean supportedRuntime() {
        if (FMLEnvironment.dist != Dist.CLIENT) return false;
        var mods = LoadingModList.get().getMods();
        return mods.stream().anyMatch(m -> m.getModId().equals("oculus") && m.getVersion().toString().equals("1.8.0"))
                && mods.stream().anyMatch(m -> m.getModId().equals("embeddium") && m.getVersion().toString().equals("0.3.31+mc1.20.1"));
    }
    @Override public void onLoad(String name) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        return FMLEnvironment.dist == Dist.CLIENT && (target.startsWith("net.minecraft.") || supportedRuntime());
    }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
