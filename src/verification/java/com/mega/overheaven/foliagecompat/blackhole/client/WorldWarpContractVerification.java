package com.mega.overheaven.foliagecompat.blackhole.client;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/** Checks compiled hooks against the exact mapped runtime jars without launching a game. */
public final class WorldWarpContractVerification {
    private static int checks;
    private static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); checks++; }
    private static Object value(AnnotationNode a, String key) {
        if (a.values != null) for (int i=0;i<a.values.size();i+=2)
            if (a.values.get(i).equals(key)) return a.values.get(i+1);
        return null;
    }
    private static List<AnnotationNode> annotations(List<AnnotationNode> a, List<AnnotationNode> b) {
        var result=new ArrayList<AnnotationNode>();
        if(a!=null)result.addAll(a);if(b!=null)result.addAll(b);return result;
    }
    private static List<?> list(Object value) { return value instanceof List<?> l ? l : value==null ? List.of() : List.of(value); }
    private static ClassNode read(String name) throws Exception {
        try(var stream=WorldWarpContractVerification.class.getClassLoader().getResourceAsStream(name.replace('.','/')+".class")) {
            check(stream!=null,"class missing: "+name);
            var node=new ClassNode();new ClassReader(stream.readAllBytes()).accept(node,0);return node;
        }
    }
    private static boolean matches(MethodNode method, String selector) {
        return selector.indexOf('(')<0 ? method.name.equals(selector) : (method.name+method.desc).equals(selector);
    }
    private static void callback(MethodNode handler, MethodNode target) {
        Type[] params=Type.getArgumentTypes(handler.desc), args=Type.getArgumentTypes(target.desc);
        check(params.length==1 || params.length==args.length+1,"callback arity: "+handler.name);
        if(params.length>1)for(int i=0;i<args.length;i++) {
            boolean coerce=false;
            if(handler.invisibleParameterAnnotations!=null && handler.invisibleParameterAnnotations[i]!=null)
                coerce=handler.invisibleParameterAnnotations[i].stream().anyMatch(a->a.desc.endsWith("/Coerce;"));
            if(handler.visibleParameterAnnotations!=null && handler.visibleParameterAnnotations[i]!=null)
                coerce|=handler.visibleParameterAnnotations[i].stream().anyMatch(a->a.desc.endsWith("/Coerce;"));
            check(params[i].equals(args[i]) || coerce && params[i].equals(Type.getType(Object.class))
                    && args[i].getSort()==Type.OBJECT,"callback argument: "+handler.name+" #"+i);
        }
    }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);
        var config=JsonParser.parseString(Files.readString(root.resolve("src/main/resources/overheaven_foliage_compat.worldwarp.mixins.json"))).getAsJsonObject();
        String pkg=config.get("package").getAsString();
        for(var name:config.getAsJsonArray("client")) {
            ClassNode mixin=read(pkg+"."+name.getAsString());
            var targets=new ArrayList<String>();
            for(var a:annotations(mixin.visibleAnnotations,mixin.invisibleAnnotations))if(a.desc.endsWith("/Mixin;")) {
                for(var t:list(value(a,"targets")))targets.add(t.toString());
                for(var t:list(value(a,"value")))targets.add(((Type)t).getClassName());
            }
            check(!targets.isEmpty(),"missing target "+name);
            for(String targetName:targets) {
                ClassNode target=read(targetName);
                for(var field:mixin.fields)for(var a:annotations(field.visibleAnnotations,field.invisibleAnnotations))if(a.desc.endsWith("/Shadow;"))
                    check(target.fields.stream().anyMatch(f->f.name.equals(field.name)&&f.desc.equals(field.desc)&&((f.access^field.access)&Opcodes.ACC_STATIC)==0),"shadow field: "+targetName+"."+field.name);
                for(var handler:mixin.methods)for(var a:annotations(handler.visibleAnnotations,handler.invisibleAnnotations)) {
                    if(a.desc.endsWith("/Shadow;"))check(target.methods.stream().anyMatch(m->m.name.equals(handler.name)&&m.desc.equals(handler.desc)),"shadow method: "+handler.name);
                    if(!a.desc.startsWith("Lorg/spongepowered/asm/mixin/injection/"))continue;
                    for(var selected:list(value(a,"method"))) {
                        var methods=target.methods.stream().filter(m->matches(m,selected.toString())).toList();
                        check(!methods.isEmpty(),"selector: "+targetName+"."+selected);
                        for(var method:methods) {
                            check(((method.access^handler.access)&Opcodes.ACC_STATIC)==0,"static callback: "+handler.name);
                            if(a.desc.endsWith("/Inject;")) callback(handler,method);
                            for(var location:list(value(a,"at"))) {
                                var at=(AnnotationNode)location;
                                if("INVOKE".equals(value(at,"value"))) {
                                    String invoke=(String)value(at,"target");
                                    int semi=invoke.indexOf(';'), paren=invoke.indexOf('(');
                                    String owner=invoke.substring(1,semi), called=invoke.substring(semi+1,paren), desc=invoke.substring(paren);
                                    int count=0;
                                    for(var insn:method.instructions)if(insn instanceof MethodInsnNode call && call.owner.equals(owner)&&call.name.equals(called)&&call.desc.equals(desc))count++;
                                    check(count>0,"invoke anchor: "+targetName+"."+selected+" -> "+invoke);
                                }
                            }
                        }
                    }
                }
            }
        }
        ClassNode culler=read("me.jellysquid.mods.sodium.client.render.chunk.occlusion.OcclusionCuller");
        var queue=culler.methods.stream().filter(m->m.name.equals("processQueue")).findFirst().orElseThrow();
        int loads=0;
        for(var insn:queue.instructions)if(insn instanceof VarInsnNode v&&v.var==8&&v.getOpcode()==Opcodes.ILOAD)loads++;
        check(loads==2,"visibility local has exactly visitor and traversal loads");
        var refmap=JsonParser.parseString(Files.readString(root.resolve("build/tmp/compileJava/overheaven_foliage_compat.refmap.json"))).getAsJsonObject().getAsJsonObject("mappings");
        for(String mixin:List.of("LevelRendererMixin","FrustumMixin","RenderTypeMixin","OculusSortingMixin"))
            check(refmap.has(pkg.replace('.','/')+"/"+mixin),"production refmap: "+mixin);
        for(String name:List.of("WorldWarpCommands","WorldWarpEvents","WorldWarpMath","WorldWarpNetwork","WorldWarpState")) {
            byte[] bytes=Files.readAllBytes(root.resolve("build/classes/java/main/com/mega/overheaven/foliagecompat/worldwarp/"+name+".class"));
            String pool=new String(bytes,java.nio.charset.StandardCharsets.ISO_8859_1);
            check(!pool.contains("net/minecraft/client/")&&!pool.contains("worldwarp/client/"),"common class isolation: "+name);
        }
        System.out.println("PASS: "+checks+" exact target, callback, shadow, invoke, refmap and common-side checks (static contracts only)");
    }
}
