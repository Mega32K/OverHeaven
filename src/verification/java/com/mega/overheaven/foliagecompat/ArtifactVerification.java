package com.mega.overheaven.foliagecompat;

import com.google.gson.JsonParser;
import com.mega.overheaven.foliagecompat.compat.SwayModelContract;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Examines the reobfuscated distributable, not only development output. */
public final class ArtifactVerification {
    private static int checks;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    private static String text(JarFile jar, String name) throws Exception {
        var entry = jar.getJarEntry(name);
        check(entry != null, "missing artifact resource " + name);
        try (var input = jar.getInputStream(entry)) { return new String(input.readAllBytes(), StandardCharsets.UTF_8); }
    }
    public static void main(String[] args) throws Exception {
        try (JarFile jar = new JarFile(args[0])) {
            var config = JsonParser.parseString(text(jar, "overheaven_foliage_compat.mixins.json")).getAsJsonObject();
            check(config.getAsJsonArray("client").size() == 1, "one client-only Mixin");
            check(!config.has("mixins") || config.getAsJsonArray("mixins").isEmpty(), "no common/client rendering Mixin leakage");
            check(config.get("plugin").getAsString().endsWith("FoliageCompatMixinPlugin"), "optional guard plugin");
            var refmap = JsonParser.parseString(text(jar, "overheaven_foliage_compat.refmap.json")).getAsJsonObject();
            check(refmap.get("mappings").isJsonObject(), "generated refmap must be parseable (stable remap=false selectors may be empty)");
            check(jar.getManifest().getMainAttributes().getValue("MixinConfigs").equals("overheaven_foliage_compat.mixins.json,overheaven_foliage_compat.blackhole.mixins.json"), "Forge Mixin manifest registration");
            var blackHoleConfig=JsonParser.parseString(text(jar,"overheaven_foliage_compat.blackhole.mixins.json")).getAsJsonObject();
            check(blackHoleConfig.getAsJsonArray("client").size()==6 && !blackHoleConfig.has("mixins"),"black hole rendering mixins client-only");
            check(blackHoleConfig.get("plugin").getAsString().endsWith("BlackHoleMixinPlugin"),"independent Oculus guard");
            try(var texture=jar.getInputStream(jar.getJarEntry("assets/overheaven_foliage_compat/textures/blackhole/disk_detail.png"))) {
                var image=javax.imageio.ImageIO.read(texture);
                check(image!=null && image.getWidth()==512 && image.getHeight()==512,"original disk material packaged");
            }
            for(String shader:new String[]{"fullscreen.vsh","copy.fsh","integrate.fsh","composite.fsh","foreground.fsh","bloom.fsh","field.glsl"}) check(!text(jar,"assets/overheaven_foliage_compat/shaders/blackhole/"+shader).isBlank(),"shader packaged "+shader);
            check(JsonParser.parseString(text(jar,"assets/overheaven_foliage_compat/blackhole/presets/reference.json")).getAsJsonObject().get("schema").getAsString().equals("overheaven:blackhole/reference-v1"),"reference preset packaged");
            check(refmap.getAsJsonObject("mappings").getAsJsonObject("com/mega/overheaven/foliagecompat/blackhole/mixin/LevelRendererMixin").get("renderLevel").getAsString().contains("m_109599_"),"vanilla render hook production refmap");
            String metadata = text(jar, "META-INF/mods.toml");
            check(!metadata.contains("$" + "{"), "expanded metadata");
            check(metadata.contains("modId=\"overheaven_foliage_compat\"") && metadata.contains("version=\"" + args[1] + "\""), "artifact identity/version");
            int remappedClientCalls = 0;
            for (var entry : java.util.Collections.list(jar.entries())) {
                check(!entry.getName().startsWith("com/github/razorplay01/") && !entry.getName().startsWith("net/karto/")
                        && !entry.getName().startsWith("fr/madu59/") && !entry.getName().startsWith("net/irisshaders/") && !entry.getName().endsWith(".jar"), "no repackaged third-party mod code");
                if (!entry.getName().endsWith(".class")) continue;
                byte[] bytes;
                try (var input = jar.getInputStream(entry)) { bytes = input.readAllBytes(); }
                check(((bytes[6] & 255) << 8 | (bytes[7] & 255)) == 61, "all mod classes must target Java 17");
                ClassNode node = new ClassNode();
                new ClassReader(bytes).accept(node, 0);
                if (node.name.endsWith("/SwayModelMixin")) {
                    check(node.methods.stream().anyMatch(m -> m.name.equals("getModelData") && m.desc.equals(SwayModelContract.MODEL_DATA)), "Forge ModelData selector remains stable");
                    check(node.methods.stream().anyMatch(m -> m.name.equals("getRenderTypes") && m.desc.equals(SwayModelContract.RENDER_TYPES)), "Forge layer selector remains stable");
                    check(node.methods.stream().anyMatch(m -> m.name.equals("getQuads") && m.desc.equals(SwayModelContract.QUADS)), "Forge quad selector remains stable");
                }
                for (var method : node.methods) for (var instruction : method.instructions) {
                    if(instruction instanceof MethodInsnNode dependency) check(!dependency.owner.startsWith("com/mega/endinglib/"),"no dependency on newer EndingLibrary depth interfaces");
                    if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/client/Minecraft")) {
                        check(!call.name.equals("getInstance") && !call.name.equals("getBlockRenderer"), "Minecraft calls must be production-remapped");
                        if (call.name.startsWith("m_")) remappedClientCalls++;
                    }
                }
            }
            check(remappedClientCalls > 0, "reobfJar produced SRG client calls");
        }
        System.out.println("PASS: " + checks + " distributable checks (Java 17, metadata, Mixin/refmap, production remapping, no repackaged mods).");
    }
}
