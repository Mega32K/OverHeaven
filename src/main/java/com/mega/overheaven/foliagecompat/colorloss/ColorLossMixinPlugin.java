package com.mega.overheaven.foliagecompat.colorloss;

import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Verify both optional hooks together, so source changes never run without their uniforms. */
public final class ColorLossMixinPlugin implements IMixinConfigPlugin {
    private Boolean supported;
    @Override public void onLoad(String name) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (FMLEnvironment.dist != Dist.CLIENT) return false;
        if (!target.startsWith("net.irisshaders.iris.")) return true;
        if (supported != null) return supported;
        supported = false;
        var mod = LoadingModList.get().getMods().stream().filter(m -> m.getModId().equals("oculus")).findFirst();
        if (mod.isEmpty() || !mod.get().getVersion().toString().equals("1.8.0")) return false;
        try {
            var file = mod.get().getOwningFile().getFile();
            ClassNode pack = new ClassNode(), uniforms = new ClassNode();
            new ClassReader(Files.readAllBytes(file.findResource("net/irisshaders/iris/shaderpack/ShaderPack.class"))).accept(pack, 0);
            new ClassReader(Files.readAllBytes(file.findResource("net/irisshaders/iris/uniforms/CommonUniforms.class"))).accept(uniforms, 0);
            boolean constructor = pack.methods.stream().anyMatch(m -> m.name.equals("<init>")
                    && m.desc.equals("(Ljava/nio/file/Path;Ljava/util/Map;Lcom/google/common/collect/ImmutableList;)V")
                    && java.util.Arrays.stream(m.instructions.toArray()).anyMatch(i -> i instanceof MethodInsnNode call
                        && call.owner.equals("net/irisshaders/iris/shaderpack/include/IncludeProcessor")
                        && call.name.equals("<init>") && call.desc.equals("(Lnet/irisshaders/iris/shaderpack/include/IncludeGraph;)V"))
                    && java.util.Arrays.stream(m.instructions.toArray()).anyMatch(i -> i instanceof MethodInsnNode call
                        && call.owner.equals("net/irisshaders/iris/shaderpack/include/IncludeGraph")
                        && call.name.equals("<init>") && call.desc.equals("(Ljava/nio/file/Path;Lcom/google/common/collect/ImmutableList;)V")));
            boolean registration = uniforms.methods.stream().anyMatch(m -> m.name.equals("addNonDynamicUniforms")
                    && m.desc.equals("(Lnet/irisshaders/iris/gl/uniform/UniformHolder;Lnet/irisshaders/iris/shaderpack/IdMap;Lnet/irisshaders/iris/shaderpack/properties/PackDirectives;Lnet/irisshaders/iris/uniforms/FrameUpdateNotifier;)V"));
            supported = constructor && registration;
        } catch (Exception error) {
            org.slf4j.LoggerFactory.getLogger("OverHeavenColorLoss").warn("Layered color loss hooks unavailable", error);
        }
        return supported;
    }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
