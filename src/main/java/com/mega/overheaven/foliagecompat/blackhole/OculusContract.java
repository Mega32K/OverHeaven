package com.mega.overheaven.foliagecompat.blackhole;

import java.util.Map;
import org.objectweb.asm.tree.ClassNode;

public final class OculusContract {
    public static final String PIPELINE="net.irisshaders.iris.pipeline.IrisRenderingPipeline";
    public static final String COMPOSITE="net.irisshaders.iris.pipeline.CompositeRenderer";
    public static final String QUAD="net.irisshaders.iris.pathways.FullScreenQuadRenderer";
    public static final String PROGRAM="net.irisshaders.iris.gl.program.Program";
    public static final String CREATE="(Lnet/irisshaders/iris/shaderpack/programs/ProgramSource;Lcom/google/common/collect/ImmutableSet;Lcom/google/common/collect/ImmutableSet;Ljava/util/function/Supplier;)Lnet/irisshaders/iris/gl/program/Program;";
    public static final java.util.List<String> TARGETS=java.util.List.of(PIPELINE,COMPOSITE,QUAD,PROGRAM);
    public static String rejection(Map<String,ClassNode> nodes) {
        if(!nodes.keySet().containsAll(TARGETS)) return "missing Oculus target classes";
        ClassNode pipeline=nodes.get(PIPELINE);
        if(pipeline.fields.stream().noneMatch(entry -> entry.name.equals("renderTargets") && entry.desc.equals("Lnet/irisshaders/iris/targets/RenderTargets;"))) return "missing renderTargets field";
        if(!method(pipeline,"beginTranslucents","()V") || !method(pipeline,"finalizeLevelRendering","()V") || !method(pipeline,"getFlippedAfterTranslucent","()Lcom/google/common/collect/ImmutableSet;")) return "pipeline method mismatch";
        if(!method(nodes.get(COMPOSITE),"createProgram",CREATE)) return "composite program factory mismatch";
        if(!method(nodes.get(QUAD),"renderQuad","()V")) return "quad method mismatch";
        if(!method(nodes.get(PROGRAM),"getProgramId","()I") || !method(nodes.get(PROGRAM),"destroyInternal","()V")) return "program lifetime mismatch";
        return null;
    }
    private static boolean method(ClassNode node,String name,String descriptor) { return node.methods.stream().anyMatch(method -> method.name.equals(name) && method.desc.equals(descriptor)); }
}
