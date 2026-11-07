package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import com.google.common.collect.ImmutableList;
import com.mega.overheaven.foliagecompat.colorloss.client.ColorLossPackAdapter;
import com.mega.overheaven.foliagecompat.worldwarp.client.WorldWarpPackAdapter;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.state.ShaderAttributeInputs;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import net.irisshaders.iris.shaderpack.include.*;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import org.lwjgl.opengl.GL;

/** Exact installed ZIP -> Oculus include/options/preprocessor/transformer -> real driver compiler. */
public final class WorldWarpPackVerification {
    private static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    private static String source(IncludeProcessor processor, String path) {
        var lines=processor.getIncludedFile(AbsolutePackPath.fromAbsolutePath(path));
        var defines=new ArrayList<>(List.of(new StringPair("MC_VERSION","12001"),new StringPair("IRIS_VERSION","10800"),
                new StringPair("IS_IRIS","1"),new StringPair("MC_OS_WINDOWS","1"),
                new StringPair("IRIS_FEATURE_CUSTOM_IMAGES","1"),new StringPair("MC_GL_VERSION","460"),
                new StringPair("MC_RENDER_QUALITY","1.0"),new StringPair("MC_SHADOW_QUALITY","1.0"),
                new StringPair("MC_NORMAL_MAP","1"),new StringPair("MC_SPECULAR_MAP","1")));
        net.irisshaders.iris.gl.shader.StandardMacros.getRenderStages().forEach((key,value) -> defines.add(new StringPair(key,value)));
        return JcppProcessor.glslPreprocessSource(String.join("\n",lines)+"\n",defines);
    }
    public static void main(String[] args) throws Exception {
        var configField=net.irisshaders.iris.Iris.class.getDeclaredField("irisConfig");
        configField.setAccessible(true);
        configField.set(null,new net.irisshaders.iris.config.IrisConfig(Path.of("unused-test-options.properties")));
        check(glfwInit(),"GLFW"); glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,4); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
        glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_COMPAT_PROFILE);
        long window=glfwCreateWindow(16,16,"worldWarp exact pack compilation",0,0); check(window!=0,"context");
        glfwMakeContextCurrent(window); GL.createCapabilities();
        try(var zip=FileSystems.newFileSystem(Path.of(args[0]))) {
            Path root=zip.getPath("/shaders");
            var roots=ImmutableList.<AbsolutePackPath>builder();
            // Match ShaderPack's actual Oculus 1.8.0 entry points, not every file in the ZIP.
            for(String directory:new String[]{"/","/world0","/world-1","/world1"})
                ShaderPackSourceNames.findPresentSources(roots,root,AbsolutePackPath.fromAbsolutePath(directory),ShaderPackSourceNames.POTENTIAL_STARTS);
            IncludeGraph raw=new IncludeGraph(root,roots.build());
            check(!raw.getNodes().containsKey(AbsolutePackPath.fromAbsolutePath("/program/gbuffers_lightning.glsl")),
                    "unused lightning program must not be added to the runtime graph");
            String[] programs=new String[]{"gbuffers_basic","gbuffers_line","gbuffers_block","gbuffers_damagedblock","gbuffers_terrain","gbuffers_entities","gbuffers_hand","gbuffers_water","gbuffers_hand_water","gbuffers_textured",
                    "gbuffers_beaconbeam","gbuffers_spidereyes","gbuffers_skybasic","gbuffers_skytextured","gbuffers_clouds",
                    "gbuffers_weather","gbuffers_armor_glint","gbuffers_lightning","deferred1","composite","composite1","composite4","composite5","composite6"};
            List<Map<String,String>> options=List.of(Map.of(),
                    Map.of("COLORED_LIGHTING","256","WORLD_SPACE_REFLECTIONS","1","SKY_EFFECT_REFLECTION_DEFINE","2","MOTION_BLUR_EFFECT","1"),
                    Map.of("RP_MODE","3","SHADER_STYLE","1","BLOOM_ENABLED","-1","TAA","false"));
            if (args.length > 2 && args[2].equals("default")) options=List.of(Map.of());
            int compiled=0;
            for(int variant=0;variant<options.size();variant++) {
                IncludeGraph selected=new ShaderPackOptions(raw,options.get(variant)).getIncludes();
                IncludeGraph colored=ColorLossPackAdapter.adapt(selected,root);
                check(colored!=selected,"existing color loss adaptation failed");
                IncludeGraph adapted=WorldWarpPackAdapter.adapt(colored,root);
                check(adapted!=colored,"atomic source adaptation failed, variant "+variant);
                // Raw persistent light/cache producers must remain byte-identical.
                for(var entry:selected.getNodes().entrySet()) {
                    String path=entry.getKey().getPathString();
                    if(path.equals("/program/shadowcomp.glsl")||path.equals("/program/shadow.glsl"))
                        check(entry.getValue().getLines().equals(adapted.getNodes().get(entry.getKey()).getLines()),"cache producer changed");
                }
                IncludeProcessor processor=new IncludeProcessor(adapted);
                for(String dimension:new String[]{"world0","world-1","world1"}) for(String name:programs) {
                    if (variant > 0 && !java.util.Set.of("gbuffers_terrain","gbuffers_water","gbuffers_textured","deferred1","composite","composite1","composite4","composite6").contains(name)) continue;
                    String path="/"+dimension+"/"+name;
                    if(!selected.getNodes().containsKey(AbsolutePackPath.fromAbsolutePath(path+".fsh"))) continue;
                    String vertex=source(processor,path+".vsh"), fragment=source(processor,path+".fsh");
                    Map<PatchShaderType,String> transformed;
                    if(name.startsWith("gbuffers")) {
                        transformed=TransformPatcher.patchVanilla(name,vertex,null,null,null,fragment,AlphaTest.ALWAYS,
                                name.equals("gbuffers_line"),false,new ShaderAttributeInputs(true,true,true,true,true),new Object2ObjectOpenHashMap<>());
                    } else {
                        transformed=TransformPatcher.patchComposite(name,vertex,null,fragment,
                                name.startsWith("deferred")?TextureStage.DEFERRED:TextureStage.COMPOSITE_AND_FINAL,new Object2ObjectOpenHashMap<>());
                    }
                    try(var program=new GlProgram(transformed.get(PatchShaderType.VERTEX),transformed.get(PatchShaderType.FRAGMENT))) {
                        if(name.equals("gbuffers_textured"))
                            check(glGetUniformLocation(program.handle(),"ohColorLossResidual")>=0,"particle program must consume layered color loss");
                        if(name.equals("gbuffers_terrain")) check(glGetUniformLocation(program.handle(),"ohWarpSlope")>=0,"terrain must consume worldWarp");
                        if(name.equals("gbuffers_hand") || name.equals("gbuffers_skybasic")) check(glGetUniformLocation(program.handle(),"ohWarpSlope")==-1,"hand/sky must bypass worldWarp");
                        compiled++;
                    }
                    catch(Exception error) {
                        Path failures=Path.of(args[1]); Files.createDirectories(failures);
                        Files.writeString(failures.resolve(variant+"-"+dimension+"-"+name+".fsh"),transformed.get(PatchShaderType.FRAGMENT));
                        Files.writeString(failures.resolve(variant+"-"+dimension+"-"+name+".vsh"),transformed.get(PatchShaderType.VERTEX));
                        throw new IllegalStateException("variant="+variant+" "+path,error);
                    }
                    if(name.equals("gbuffers_terrain")) {
                        var sodium=TransformPatcher.patchSodium(name,vertex,null,null,null,fragment,AlphaTest.ALWAYS,
                                new ShaderAttributeInputs(true,true,true,true,true),new Object2ObjectOpenHashMap<>());
                        try(var program=new GlProgram(sodium.get(PatchShaderType.VERTEX),sodium.get(PatchShaderType.FRAGMENT))) { compiled++; }
                    }
                }
                System.out.println("Compiled exact pack variant "+variant+" in all three dimensions");
            }
            IncludeGraph changedAnchor=raw.map(path -> (line,text) -> path.getPathString().equals("/program/gbuffers_terrain.glsl")
                    && text.contains("gl_Position = gl_ProjectionMatrix * gbufferModelView * position;") ? "gl_Position = vec4(0.0);" : text);
            check(WorldWarpPackAdapter.adapt(changedAnchor,root)==changedAnchor,"mismatched anchors must retain the entire input graph");
            check(WorldWarpPackAdapter.adapt(raw,Path.of("unadapted-directory"))==raw,"unsupported pack must retain all source");
            System.out.println("PASS: "+compiled+" Oculus-transformed program pairs, color loss composition, hand/sky exclusions, immutable caches");
        } finally { glfwDestroyWindow(window); glfwTerminate(); }
    }
}
