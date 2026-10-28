package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.opengl.GL33C.*;
import java.util.function.Consumer;

/** Current-frame environment only: a paired scene/opaque result, never subject history. */
final class GlCausalBackground implements AutoCloseable {
    final GlTarget result=new GlTarget(GL_RGBA16F,GL_RGBA16F);
    private GlProgram program;
    GlCausalBackground() { }
    GlCausalBackground(String vertex,String fragment) { program=new GlProgram(vertex,fragment); }
    void render(int scene,int opaque,int opaqueDepth,int surfaceDepth,int transmission,
                int width,int height,int steps,int vao,Consumer<GlProgram> uniforms) {
        if(program==null) program=new GlProgram("causal_background");
        result.resize(width,height);result.bind();GlState.fullscreen();program.use();uniforms.accept(program);
        program.texture("Scene",0,scene);program.texture("Opaque",1,opaque);
        program.texture("OpaqueDepth",2,opaqueDepth);program.texture("SurfaceDepth",3,surfaceDepth);
        program.texture("Transmission",4,transmission);program.integer("Samples",steps==24?3:steps==96?7:5);
        glBindVertexArray(vao);glDrawArrays(GL_TRIANGLES,0,3);
    }
    @Override public void close() { result.close();if(program!=null) program.close();program=null; }
}
