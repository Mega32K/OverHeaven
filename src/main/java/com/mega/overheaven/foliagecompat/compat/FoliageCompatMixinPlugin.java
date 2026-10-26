package com.mega.overheaven.foliagecompat.compat;

import com.mega.overheaven.foliagecompat.PatchLog;
import java.util.List;
import java.util.Set;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class FoliageCompatMixinPlugin implements IMixinConfigPlugin {
    private boolean enabled;

    @Override
    public void onLoad(String mixinPackage) {
        if (FMLLoader.getDist() != Dist.CLIENT) {
            PatchLog.LOGGER.info("Client foliage patch skipped on dedicated server.");
            return;
        }
        var mods = LoadingModList.get();
        if (mods == null) {
            PatchLog.LOGGER.warn("Foliage patch skipped: Forge loading mod list is unavailable.");
            return;
        }
        var versions = new java.util.HashMap<String, String>();
        mods.getMods().forEach(m -> versions.put(m.getModId(), m.getVersion().toString()));
        String foliage = versions.get("mc2_interactivefoliage");
        String sway = versions.get("sway");
        if (foliage == null || sway == null) {
            PatchLog.LOGGER.info("Foliage patch skipped: requires both MC2 Interactive Foliage and SWAY (found foliage={}, sway={}).", foliage, sway);
            return;
        }
        enabled = SwayModelContract.supports(versions.get("minecraft"), foliage, sway);
        if (!enabled) {
            PatchLog.LOGGER.warn("Unsupported versions; foliage patch skipped. Supported: Minecraft 1.20.1, foliage 2.2.0, SWAY 2.4.5. Found: Minecraft {}, foliage {}, SWAY {}.", versions.get("minecraft"), foliage, sway);
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!enabled || !targetClassName.equals(SwayModelContract.TARGET)) return false;
        try {
            // ModLauncher rejects getClassNode(name, false). Read the owning mod resource directly;
            // this neither loads the optional client class nor enters the transformation pipeline.
            var mods = LoadingModList.get();
            var swayFile = mods == null ? null : mods.getModFileById("sway");
            if (swayFile == null) {
                enabled = false;
                PatchLog.LOGGER.warn("Foliage patch skipped: cannot locate the owning SWAY mod file.");
                return false;
            }
            ClassNode node = SwayModelContract.read(swayFile.getFile().findResource(SwayModelContract.CLASS_RESOURCE));
            String reason = SwayModelContract.rejection(node);
            if (reason == null) return true;
            PatchLog.LOGGER.warn("Foliage patch skipped: incompatible SwayModel bytecode: {}.", reason);
        } catch (Exception | LinkageError exception) {
            PatchLog.LOGGER.warn("Foliage patch skipped: cannot inspect optional SwayModel target.", exception);
        }
        enabled = false;
        return false;
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String name, ClassNode target, String mixin, IMixinInfo info) { }
    @Override public void postApply(String name, ClassNode target, String mixin, IMixinInfo info) {
        PatchLog.LOGGER.info("Applied SWAY Forge model delegation patch (ModelData, render layers, extended quads). Diagnostics: {}.", PatchLog.DIAGNOSTICS);
    }
}
