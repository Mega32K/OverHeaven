package com.mega.overheaven.foliagecompat;

import com.mega.overheaven.foliagecompat.compat.SwayModelContract;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Headless contract checks against the supplied versioned jars, not a game launch. */
public final class ContractVerification {
    private static int checks;
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        checks++;
    }
    private static ClassNode read(InputStream input) throws Exception {
        try (input) { ClassNode node = new ClassNode(); new ClassReader(input).accept(node, 0); return node; }
    }
    private static ClassNode jarClass(String jar, String name) throws Exception {
        try (JarFile file = new JarFile(jar)) { return read(file.getInputStream(file.getJarEntry(name.replace('.', '/') + ".class"))); }
    }
    private static boolean declared(ClassNode node, String name, String desc) {
        return node.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(desc));
    }
    private static void verifyKeyBindingApi(String foliageJar) throws Exception {
        String resourceClass = "net/minecraft/resources/ResourceLocation";
        var resource = ClassLoader.getSystemResourceAsStream(resourceClass + ".class");
        check(resource != null, "selected Forge ResourceLocation class must be available");
        ClassNode available = read(resource);
        ClassNode keys = jarClass(foliageJar, "net.karto.mc2.mc2_interactivefoliage.platform.forge.ForgeKeyBindings");
        int calls = 0;
        for (var method : keys.methods) for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC && call.owner.equals(resourceClass)) {
                int access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
                check(available.methods.stream().anyMatch(candidate -> candidate.name.equals(call.name) && candidate.desc.equals(call.desc) && (candidate.access & access) == access),
                        "Interactive Foliage key bindings require ResourceLocation." + call.name + call.desc + "; selected Forge API is too old. Use supported Forge 47.4.10 or newer.");
                calls++;
            }
        }
        check(calls > 0, "actual Interactive Foliage ResourceLocation factory calls must be inspected");
    }
    private static void delegates(ClassNode mixin, String name, String descriptor) {
        MethodNode method = mixin.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).findFirst().orElseThrow();
        int calls = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/client/resources/model/BakedModel")) {
                check(call.name.equals(name) && call.desc.equals(descriptor), name + " must keep Forge data/layer overload");
                calls++;
            }
        }
        check(calls == 1, name + " must delegate exactly once to its original parent");
    }
    public static void main(String[] args) throws Exception {
        check(SwayModelContract.supports("1.20.1", "2.2.0", "2.4.5"), "supported version tuple");
        check(!SwayModelContract.supports("1.20.1", null, "2.4.5"), "missing foliage");
        check(!SwayModelContract.supports("1.20.1", "2.2.0", null), "missing SWAY");
        check(!SwayModelContract.supports("1.20.2", "2.2.0", "2.4.5"), "unsupported Minecraft");
        check(!SwayModelContract.supports("1.20.1", "2.3.0", "2.4.5"), "unsupported foliage");
        check(!SwayModelContract.supports("1.20.1", "2.2.0", "2.5.0"), "unsupported SWAY");
        ClassNode sway = jarClass(args[0], SwayModelContract.TARGET);
        check(SwayModelContract.rejection(sway) == null, "actual SwayModel contract must match");
        ClassNode badField = jarClass(args[0], SwayModelContract.TARGET);
        badField.fields.removeIf(f -> f.name.equals("parent"));
        check(SwayModelContract.rejection(badField) != null, "missing parent must skip");
        ClassNode badMethod = jarClass(args[0], SwayModelContract.TARGET);
        badMethod.methods.removeIf(m -> m.name.equals("transformQuads"));
        check(SwayModelContract.rejection(badMethod) != null, "missing deformation method must skip");
        ClassNode existingFix = jarClass(args[0], SwayModelContract.TARGET);
        existingFix.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "getModelData", SwayModelContract.MODEL_DATA, null, null));
        check(SwayModelContract.rejection(existingFix) != null, "an upstream Forge delegation implementation must skip");
        ClassNode foliage = jarClass(args[1], "net.karto.mc2.mc2_interactivefoliage.platform.forge.ForgeFoliageModel");
        check(declared(foliage, "getModelData", SwayModelContract.MODEL_DATA), "foliage ModelData signature");
        check(declared(foliage, "getRenderTypes", SwayModelContract.RENDER_TYPES), "foliage render layer signature");
        check(declared(foliage, "getQuads", SwayModelContract.QUADS), "foliage extended quad signature");
        verifyKeyBindingApi(args[1]);
        ClassNode mixin = read(Files.newInputStream(Path.of(args[2], "com/mega/overheaven/foliagecompat/mixin/SwayModelMixin.class")));
        delegates(mixin, "getModelData", SwayModelContract.MODEL_DATA);
        delegates(mixin, "getRenderTypes", SwayModelContract.RENDER_TYPES);
        delegates(mixin, "getQuads", SwayModelContract.QUADS);
        check(mixin.fields.stream().filter(f -> f.desc.equals(SwayModelContract.MODEL)).count() == 1,
                "only the existing shadow parent is referenced; no new wrapper cache");
        for (String name : new String[]{"FoliageCompat", "PatchLog", "compat/FoliageCompatMixinPlugin", "compat/SwayModelContract"}) {
            ClassNode common = read(Files.newInputStream(Path.of(args[2], "com/mega/overheaven/foliagecompat/" + name + ".class")));
            for (var method : common.methods) for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    check(!call.owner.startsWith("net/minecraft/client/") && !call.owner.startsWith("com/github/razorplay01/")
                            && !call.owner.startsWith("net/karto/"), "common/plugin code must not invoke client or optional classes");
                }
            }
        }
        System.out.println("PASS: " + checks + " bytecode/version/runtime-API/side-isolation checks against pinned published jars.");
    }
}
