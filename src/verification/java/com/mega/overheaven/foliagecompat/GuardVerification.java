package com.mega.overheaven.foliagecompat;

import com.mega.overheaven.foliagecompat.compat.SwayModelContract;
import cpw.mods.jarhandling.SecureJar;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.launch.MixinLaunchPluginLegacy;

/** Reproduces the 1.0.0 guard failure and exercises its replacement using Forge's actual union filesystem. */
public final class GuardVerification {
    private static int checks;

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }

    public static void main(String[] args) throws Exception {
        boolean reproduced = false;
        try {
            new MixinLaunchPluginLegacy().getClassNode(SwayModelContract.TARGET, false);
        } catch (IllegalArgumentException exception) {
            reproduced = exception.getMessage().contains("does not currently support retrieval of untransformed bytecode");
        }
        check(reproduced, "must reproduce the exact ModLauncher failure from latest.log without starting Minecraft");

        SecureJar sway = SecureJar.from(Path.of(args[0]));
        // This mirrors ModFile.findResource's SecureJar.getPath call, rather than a classloader resource lookup.
        Path model = sway.getPath(SwayModelContract.CLASS_RESOURCE);
        try (var ownedFilesystem = model.getFileSystem()) {
            check("union".equals(ownedFilesystem.provider().getScheme()), "exercise Forge's union filesystem provider");
            ClassNode node = SwayModelContract.read(model);
            check(SwayModelContract.rejection(node) == null, "accept actual SWAY bytecode through the production reader");
            check(ownedFilesystem.isOpen(), "the production reader must not close Forge's filesystem");
            check(SwayModelContract.rejection(SwayModelContract.read(model)) == null, "repeat guard inspection without retaining/closing stale resources");
            boolean missingRejected = false;
            try {
                SwayModelContract.read(sway.getPath("overheaven_absent_target.class"));
            } catch (IOException exception) {
                missingRejected = true;
            }
            check(missingRejected, "missing class must fail for the plugin to skip safely");
        }

        Path plugin = Path.of(args[1], "com/mega/overheaven/foliagecompat/compat/FoliageCompatMixinPlugin.class");
        ClassNode compiled = new ClassNode();
        new ClassReader(Files.readAllBytes(plugin)).accept(compiled, 0);
        int resourceReads = 0;
        int contractReads = 0;
        for (var method : compiled.methods) for (var instruction : method.instructions) {
            if (!(instruction instanceof MethodInsnNode call)) continue;
            check(!call.owner.equals("org/spongepowered/asm/service/MixinService")
                    && !call.name.equals("getBytecodeProvider") && !call.name.equals("getClassNode"),
                    "plugin must never call the unsupported ModLauncher bytecode-provider path");
            if (!method.name.equals("shouldApplyMixin")) continue;
            if (call.name.equals("findResource")) resourceReads++;
            if (call.owner.equals(SwayModelContract.class.getName().replace('.', '/')) && call.name.equals("read")) contractReads++;
        }
        check(resourceReads == 1 && contractReads == 1, "shouldApplyMixin must use the tested raw-resource reader exactly once");
        System.out.println("PASS: " + checks + " guard regression checks (exact ModLauncher failure reproduced; actual SWAY read via SecureJar union path; no unsupported provider calls).");
    }
}
