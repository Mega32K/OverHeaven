package com.mega.overheaven.foliagecompat.compat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

/** Checks bytecode without linking any optional mod or Minecraft client class. */
public final class SwayModelContract {
    public static final String TARGET = "com.github.razorplay01.sway.platform.forge.util.SwayModel";
    public static final String CLASS_RESOURCE = TARGET.replace('.', '/') + ".class";
    public static final String MODEL = "Lnet/minecraft/client/resources/model/BakedModel;";
    public static final String STATE = "Lnet/minecraft/world/level/block/state/BlockState;";
    public static final String POS = "Lnet/minecraft/core/BlockPos;";
    public static final String RANDOM = "Lnet/minecraft/util/RandomSource;";
    public static final String DATA = "Lnet/minecraftforge/client/model/data/ModelData;";
    public static final String MODEL_DATA = "(Lnet/minecraft/world/level/BlockAndTintGetter;" + POS + STATE + DATA + ")" + DATA;
    public static final String RENDER_TYPES = "(" + STATE + RANDOM + DATA + ")Lnet/minecraftforge/client/ChunkRenderTypeSet;";
    public static final String QUADS = "(" + STATE + "Lnet/minecraft/core/Direction;" + RANDOM + DATA + "Lnet/minecraft/client/renderer/RenderType;)Ljava/util/List;";
    public static final String DEFORM = "(Ljava/util/List;" + STATE + "Lcom/github/razorplay01/sway/client/SwayData;" + POS + ")Ljava/util/List;";

    private SwayModelContract() { }

    /** Works with Forge's SecureJar/union paths; closes only the input stream, never Forge's filesystem. */
    public static ClassNode read(Path classFile) throws IOException {
        try (var input = Files.newInputStream(classFile)) {
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    public static boolean supports(String minecraft, String foliage, String sway) {
        return "1.20.1".equals(minecraft) && "2.2.0".equals(foliage) && "2.4.5".equals(sway);
    }

    public static String rejection(ClassNode node) {
        if (!TARGET.replace('.', '/').equals(node.name)) return "unexpected target class";
        if (!"java/lang/Object".equals(node.superName) || !node.interfaces.contains("net/minecraft/client/resources/model/BakedModel"))
            return "unexpected SwayModel inheritance";
        if (node.fields.stream().noneMatch(f -> f.name.equals("parent") && f.desc.equals(MODEL)
                && (f.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL | Opcodes.ACC_STATIC))
                == (Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL))) return "missing private final BakedModel parent";
        if (!hasMethod(node, "<init>", "(" + MODEL + ")V", 0)) return "missing model constructor";
        if (!hasMethod(node, "resolveSwayPos", "(" + POS + STATE + ")" + POS, Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC))
            return "missing private static resolveSwayPos";
        if (!hasMethod(node, "transformQuads", DEFORM, Opcodes.ACC_PRIVATE)) return "missing private transformQuads";
        for (var method : node.methods) {
            if ((method.name.equals("getModelData") && method.desc.equals(MODEL_DATA))
                    || (method.name.equals("getRenderTypes") && method.desc.equals(RENDER_TYPES))
                    || (method.name.equals("getQuads") && method.desc.equals(QUADS))) {
                return "target already declares Forge extension " + method.name + method.desc;
            }
        }
        return null;
    }

    private static boolean hasMethod(ClassNode node, String name, String desc, int requiredAccess) {
        return node.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(desc)
                && (m.access & requiredAccess) == requiredAccess);
    }
}
