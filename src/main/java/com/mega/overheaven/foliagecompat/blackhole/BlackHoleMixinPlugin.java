package com.mega.overheaven.foliagecompat.blackhole;

import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class BlackHoleMixinPlugin implements IMixinConfigPlugin {
    private Boolean supported;
    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target,String mixin) {
        if(FMLEnvironment.dist!=Dist.CLIENT) return false;
        if(!target.startsWith("net.irisshaders.iris.")) return true;
        if(supported!=null) return supported;
        supported=false;
        var mod=LoadingModList.get().getMods().stream().filter(entry -> entry.getModId().equals("oculus")).findFirst();
        if(mod.isEmpty()) return false;
        try {
            if(!mod.get().getVersion().toString().equals("1.8.0")) throw new IllegalStateException("Oculus version "+mod.get().getVersion());
            var nodes=new java.util.HashMap<String,ClassNode>();
            for(String name:OculusContract.TARGETS) {
                var file=mod.get().getOwningFile().getFile().findResource(name.replace('.','/')+".class"); ClassNode node=new ClassNode();
                new ClassReader(Files.readAllBytes(file)).accept(node,ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES); nodes.put(name,node);
            }
            String rejection=OculusContract.rejection(nodes); if(rejection!=null) throw new IllegalStateException(rejection);
            supported=true;
        } catch(Exception exception) { org.slf4j.LoggerFactory.getLogger("OverHeavenBlackHole").error("Oculus black hole adapter skipped: {}",exception.toString()); }
        return supported;
    }
    @Override public void acceptTargets(Set<String> mine,Set<String> others) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target,ClassNode node,String mixin,IMixinInfo info) { }
    @Override public void postApply(String target,ClassNode node,String mixin,IMixinInfo info) { }
}
