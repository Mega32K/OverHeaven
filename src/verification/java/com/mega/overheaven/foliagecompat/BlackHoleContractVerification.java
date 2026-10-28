package com.mega.overheaven.foliagecompat;

import com.mega.overheaven.foliagecompat.blackhole.OculusContract;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

public final class BlackHoleContractVerification {
    private static int checks;
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); checks++; }
    private static ClassNode node(byte[] bytes) { ClassNode result=new ClassNode(); new ClassReader(bytes).accept(result,0); return result; }
    public static void main(String[] arguments) throws Exception {
        var nodes=new HashMap<String,ClassNode>();
        try(var jar=new JarFile(arguments[0])) {
            for(String target:OculusContract.TARGETS) try(var stream=jar.getInputStream(jar.getJarEntry(target.replace('.','/')+".class"))) { nodes.put(target,node(stream.readAllBytes())); }
        }
        try(var jar=new JarFile(arguments[0])) {
            String[][] skyApi={
                {"shaderpack/ShaderPack","getShaderPackOptions","()Lnet/irisshaders/iris/shaderpack/option/ShaderPackOptions;"},
                {"shaderpack/option/ShaderPackOptions","getOptionValues","()Lnet/irisshaders/iris/shaderpack/option/values/OptionValues;"},
                {"shaderpack/option/values/OptionValues","getStringValueOrDefault","(Ljava/lang/String;)Ljava/lang/String;"}
            };
            for(String[] api:skyApi) {
                try(var stream=jar.getInputStream(jar.getJarEntry("net/irisshaders/iris/"+api[0]+".class"))) {
                    check(node(stream.readAllBytes()).methods.stream().anyMatch(m->m.name.equals(api[1])&&m.desc.equals(api[2])),"actual Oculus pack sky API "+api[1]);
                }
            }
        }
        check(OculusContract.rejection(nodes)==null,"actual Oculus 1.8.0 injection contract");
        for(String target:OculusContract.TARGETS) { var removed=nodes.remove(target); check(OculusContract.rejection(nodes)!=null,"missing target safely rejected "+target); nodes.put(target,removed); }
        var pipeline=nodes.get(OculusContract.PIPELINE); var field=pipeline.fields.stream().filter(value -> value.name.equals("renderTargets")).findFirst().orElseThrow();
        pipeline.fields.remove(field); check(OculusContract.rejection(nodes)!=null,"changed render target field rejected"); pipeline.fields.add(field);
        var composite=nodes.get(OculusContract.COMPOSITE); var factory=composite.methods.stream().filter(value -> value.name.equals("createProgram")).findFirst().orElseThrow();
        String descriptor=factory.desc; factory.desc="()V"; check(OculusContract.rejection(nodes)!=null,"changed program descriptor rejected"); factory.desc=descriptor;
        var minecraftResource=ClassLoader.getSystemResource("net/minecraft/client/renderer/LevelRenderer.class");
        check(minecraftResource!=null,"mapped Minecraft class available");
        var minecraftJar=Path.of(((java.net.JarURLConnection)minecraftResource.openConnection()).getJarFileURL().toURI());
        try(var jar=new JarFile(minecraftJar.toFile())) {
            ClassNode level;
            try(var stream=jar.getInputStream(jar.getJarEntry("net/minecraft/client/renderer/LevelRenderer.class"))) { level=node(stream.readAllBytes()); }
            var render=level.methods.stream().filter(method -> method.name.equals("renderLevel")).findFirst().orElseThrow();
            int translucent=0, after=0;
            for(var instruction:render.instructions) if(instruction instanceof LdcInsnNode constant) { if("translucent".equals(constant.cst)) translucent++; if("string".equals(constant.cst)) after++; }
            check(translucent==2 && after==2,"both vanilla fancy/fabulous capture injection points");
            check(render.desc.equals("(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V"),"vanilla frame injection descriptor");
            ClassNode game;
            try(var stream=jar.getInputStream(jar.getJarEntry("net/minecraft/client/renderer/GameRenderer.class"))) { game=node(stream.readAllBytes()); }
            var gameRender=game.methods.stream().filter(method -> method.name.equals("renderLevel")).findFirst().orElseThrow();
            boolean levelCall=false, stageAfter=false, handAfter=false;
            for(var instruction:gameRender.instructions) if(instruction instanceof MethodInsnNode call) {
                if(call.owner.endsWith("/LevelRenderer") && call.name.equals("renderLevel")) levelCall=true;
                if(levelCall && call.owner.endsWith("/ForgeHooksClient") && call.name.equals("dispatchRenderStage")) stageAfter=true;
                if(stageAfter && call.name.equals("renderItemInHand")) handAfter=true;
            }
            check(levelCall && stageAfter && handAfter,"AFTER_LEVEL occurs after level and before hand");
        }
        Path classes=Path.of(arguments[1]); String base="com/mega/overheaven/foliagecompat/";
        try(var paths=Files.walk(classes.resolve(base+"blackhole"))) {
            for(Path file:paths.filter(path -> path.toString().endsWith(".class")).toList()) {
                ClassNode current=node(Files.readAllBytes(file));
                if(current.name.contains("/client/") || current.name.contains("/mixin/")) continue;
                for(var member:current.fields) check(!member.desc.contains("net/minecraft/client/") && !member.desc.contains("net/irisshaders/"),"no client/optional type in common field "+current.name);
                for(var method:current.methods) {
                    check(!method.desc.contains("net/minecraft/client/") && !method.desc.contains("net/irisshaders/"),"no client/optional type in common method "+current.name);
                    for(var instruction:method.instructions) if(instruction instanceof MethodInsnNode call) check(!call.owner.startsWith("net/minecraft/client/") && !call.owner.startsWith("net/irisshaders/"),"common code never invokes Minecraft client or Oculus");
                }
            }
        }
        try(var loader=new java.net.URLClassLoader(new java.net.URL[]{classes.toUri().toURL()},BlackHoleContractVerification.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                if(name.startsWith("net.minecraft.client.") || name.startsWith("net.irisshaders.") || name.contains("foliagecompat.blackhole.client.")) throw new ClassNotFoundException("client unavailable: "+name);
                if(name.startsWith("com.mega.overheaven.foliagecompat.blackhole.")) synchronized(getClassLoadingLock(name)) {
                    Class<?> result=findLoadedClass(name); if(result==null) result=findClass(name); if(resolve) resolveClass(result); return result;
                }
                return super.loadClass(name,resolve);
            }
        }) {
            for(String name:new String[]{"BlackHole","BlackHoleStore","BlackHoleReplica","BlackHoleEvents","BlackHoleCommands","BlackHoleNetwork","BlackHoleNetwork$Update","BlackHoleMixinPlugin","OculusContract"}) {
                Class<?> type=Class.forName("com.mega.overheaven.foliagecompat.blackhole."+name,false,loader); type.getDeclaredFields(); type.getDeclaredMethods(); check(true,"server-side linkage without client classes "+name);
            }
        }
        System.out.println("PASS: "+checks+" black hole API/injection/client-isolation checks against actual Oculus and Forge artifacts.");
    }
}
