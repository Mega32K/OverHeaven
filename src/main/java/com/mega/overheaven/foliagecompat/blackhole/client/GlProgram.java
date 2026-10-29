package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.opengl.GL33C.*;
import com.mega.overheaven.foliagecompat.FoliageCompat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

final class GlProgram implements AutoCloseable {
    private final Map<String, Integer> locations = new HashMap<>();
    private int program;
    GlProgram(String fragment) {
        this(read("fullscreen.vsh"),read(fragment+".fsh"));
    }
    GlProgram(String vertexSource,String fragmentSource) {
        int vertex = 0, pixel = 0;
        try {
            vertex = compile(GL_VERTEX_SHADER, vertexSource); pixel = compile(GL_FRAGMENT_SHADER, fragmentSource);
            program = glCreateProgram(); glAttachShader(program, vertex); glAttachShader(program, pixel); glLinkProgram(program);
            if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) throw new IllegalStateException(glGetProgramInfoLog(program));
        } catch (Exception exception) { close(); throw new IllegalStateException("Black hole shader", exception); }
        finally { if (vertex != 0) glDeleteShader(vertex); if (pixel != 0) glDeleteShader(pixel); }
    }
    static String read(String file) { try { return source(file); } catch(java.io.IOException exception) { throw new IllegalStateException("Missing black hole shader "+file,exception); } }
    int handle() { return program; }
    private static String source(String file) throws java.io.IOException {
        try (var reader = Minecraft.getInstance().getResourceManager().openAsReader(new ResourceLocation(FoliageCompat.MOD_ID, "shaders/blackhole/" + file))) {
            String source = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
            for(String include:new String[]{"finale_remnant.glsl","finale_cosmos.glsl","finale_energy.glsl","singularity_glare.glsl","finale_sky_common.glsl","finale_star_style.glsl"}) {
                String directive="#include \""+include+"\"";
                if(source.contains(directive)) source=source.replace(directive,source(include));
            }
            return source.contains("#include \"field.glsl\"") ? source.replace("#include \"field.glsl\"", source("field.glsl")) : source;
        }
    }
    private static int compile(int type, String source) {
        int shader = glCreateShader(type); glShaderSource(shader, source); glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) { String error = glGetShaderInfoLog(shader); glDeleteShader(shader); throw new IllegalStateException(error); }
        return shader;
    }
    private int uniform(String name) { return locations.computeIfAbsent(name, key -> glGetUniformLocation(program, key)); }
    void use() { glUseProgram(program); }
    void integer(String name, int value) { glUniform1i(uniform(name), value); }
    void scalar(String name, double value) { glUniform1f(uniform(name), (float) value); }
    void floats(String name, float[] values) { glUniform1fv(uniform(name), values); }
    void pair(String name, float first, float second) { glUniform2f(uniform(name), first, second); }
    void vector(String name, float first, float second, float third) { glUniform3f(uniform(name), first, second, third); }
    void matrix(String name, Matrix4f matrix) { try (var stack = MemoryStack.stackPush()) { glUniformMatrix4fv(uniform(name), false, matrix.get(stack.mallocFloat(16))); } }
    void texture(String name, int unit, int texture) { glActiveTexture(GL_TEXTURE0 + unit); glBindTexture(GL_TEXTURE_2D, texture); glBindSampler(unit, 0); integer(name, unit); }
    @Override public void close() { if (program != 0) glDeleteProgram(program); program = 0; locations.clear(); }
}
