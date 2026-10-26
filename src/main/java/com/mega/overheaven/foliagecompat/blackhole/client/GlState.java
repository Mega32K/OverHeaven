package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.opengl.GL33C.*;

final class GlState implements AutoCloseable {
    private static final GlState[] POOL={new GlState(),new GlState(),new GlState(),new GlState()};
    private static int nesting;
    private int draw, read, program, vertexArray, active;
    private int sourceRgb, destinationRgb, sourceAlpha, destinationAlpha, equationRgb, equationAlpha;
    private final int[][] indexedBlend=new int[16][6];
    private final boolean[] indexedEnabled=new boolean[16];
    private final java.nio.ByteBuffer[] indexedMasks=new java.nio.ByteBuffer[16];
    private int drawBuffers;
    private boolean independentBlend;
    private final int[] viewport=new int[4], scissor=new int[4], textures=new int[11], samplers=new int[11];
    private boolean blend, depth, cull, clip, srgb, stencil, depthWrite;
    private final java.nio.ByteBuffer colorWrite=org.lwjgl.BufferUtils.createByteBuffer(4);
    static GlState capture() {
        if(nesting==POOL.length) throw new IllegalStateException("Nested black hole GL state scopes");
        GlState state=POOL[nesting]; state.save(); nesting++; return state;
    }
    private void save() {
        if(drawBuffers==0) {
            drawBuffers=glGetInteger(GL_MAX_DRAW_BUFFERS); if(drawBuffers>16) throw new IllegalStateException("Unsupported draw buffer count "+drawBuffers);
            for(int index=0;index<drawBuffers;index++) indexedMasks[index]=org.lwjgl.BufferUtils.createByteBuffer(4);
        }
        independentBlend=org.lwjgl.opengl.GL.getCapabilities().GL_ARB_draw_buffers_blend || org.lwjgl.opengl.GL.getCapabilities().OpenGL40;
        draw=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING); read=glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        program=glGetInteger(GL_CURRENT_PROGRAM); vertexArray=glGetInteger(GL_VERTEX_ARRAY_BINDING); active=glGetInteger(GL_ACTIVE_TEXTURE);
        sourceRgb=glGetInteger(GL_BLEND_SRC_RGB); destinationRgb=glGetInteger(GL_BLEND_DST_RGB); sourceAlpha=glGetInteger(GL_BLEND_SRC_ALPHA); destinationAlpha=glGetInteger(GL_BLEND_DST_ALPHA); equationRgb=glGetInteger(GL_BLEND_EQUATION_RGB); equationAlpha=glGetInteger(GL_BLEND_EQUATION_ALPHA);
        for(int index=0;index<drawBuffers;index++) {
            indexedEnabled[index]=glIsEnabledi(GL_BLEND,index); glGetBooleani_v(GL_COLOR_WRITEMASK,index,indexedMasks[index]);
            if(independentBlend) {
                indexedBlend[index][0]=glGetIntegeri(GL_BLEND_SRC_RGB,index); indexedBlend[index][1]=glGetIntegeri(GL_BLEND_DST_RGB,index); indexedBlend[index][2]=glGetIntegeri(GL_BLEND_SRC_ALPHA,index); indexedBlend[index][3]=glGetIntegeri(GL_BLEND_DST_ALPHA,index);
                indexedBlend[index][4]=glGetIntegeri(GL_BLEND_EQUATION_RGB,index); indexedBlend[index][5]=glGetIntegeri(GL_BLEND_EQUATION_ALPHA,index);
            }
        }
        blend=glIsEnabled(GL_BLEND); depth=glIsEnabled(GL_DEPTH_TEST); cull=glIsEnabled(GL_CULL_FACE); clip=glIsEnabled(GL_SCISSOR_TEST); srgb=glIsEnabled(GL_FRAMEBUFFER_SRGB); depthWrite=glGetBoolean(GL_DEPTH_WRITEMASK);
        stencil=glIsEnabled(GL_STENCIL_TEST);
        glGetIntegerv(GL_VIEWPORT,viewport); glGetIntegerv(GL_SCISSOR_BOX,scissor); glGetBooleanv(GL_COLOR_WRITEMASK,colorWrite);
        for(int unit=0;unit<textures.length;unit++) { glActiveTexture(GL_TEXTURE0+unit); textures[unit]=glGetInteger(GL_TEXTURE_BINDING_2D); samplers[unit]=glGetInteger(GL_SAMPLER_BINDING); }
        glActiveTexture(active);
    }
    static void fullscreen() {
        glDisable(GL_BLEND); glDisable(GL_DEPTH_TEST); glDisable(GL_CULL_FACE); glDisable(GL_SCISSOR_TEST); glDisable(GL_FRAMEBUFFER_SRGB);
        glDisable(GL_STENCIL_TEST);
        glDepthMask(false); glColorMask(true,true,true,true);
    }
    private static void enabled(int capability,boolean enabled) { if(enabled) glEnable(capability); else glDisable(capability); }
    @Override public void close() {
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER,draw); glBindFramebuffer(GL_READ_FRAMEBUFFER,read);
        glViewport(viewport[0],viewport[1],viewport[2],viewport[3]); glScissor(scissor[0],scissor[1],scissor[2],scissor[3]);
        glUseProgram(program); glBindVertexArray(vertexArray);
        glBlendFuncSeparate(sourceRgb,destinationRgb,sourceAlpha,destinationAlpha); glBlendEquationSeparate(equationRgb,equationAlpha);
        for(int unit=0;unit<textures.length;unit++) { glActiveTexture(GL_TEXTURE0+unit); glBindTexture(GL_TEXTURE_2D,textures[unit]); glBindSampler(unit,samplers[unit]); }
        glActiveTexture(active); enabled(GL_BLEND,blend); enabled(GL_DEPTH_TEST,depth); enabled(GL_CULL_FACE,cull); enabled(GL_SCISSOR_TEST,clip); enabled(GL_FRAMEBUFFER_SRGB,srgb);
        enabled(GL_STENCIL_TEST,stencil);
        glDepthMask(depthWrite); glColorMask(colorWrite.get(0)!=0,colorWrite.get(1)!=0,colorWrite.get(2)!=0,colorWrite.get(3)!=0); nesting--;
        for(int index=0;index<drawBuffers;index++) {
            if(indexedEnabled[index]) glEnablei(GL_BLEND,index); else glDisablei(GL_BLEND,index);
            var mask=indexedMasks[index]; glColorMaski(index,mask.get(0)!=0,mask.get(1)!=0,mask.get(2)!=0,mask.get(3)!=0);
            if(independentBlend) {
                int[] state=indexedBlend[index];
                if(org.lwjgl.opengl.GL.getCapabilities().OpenGL40) { org.lwjgl.opengl.GL40C.glBlendFuncSeparatei(index,state[0],state[1],state[2],state[3]); org.lwjgl.opengl.GL40C.glBlendEquationSeparatei(index,state[4],state[5]); }
                else { org.lwjgl.opengl.ARBDrawBuffersBlend.glBlendFuncSeparateiARB(index,state[0],state[1],state[2],state[3]); org.lwjgl.opengl.ARBDrawBuffersBlend.glBlendEquationSeparateiARB(index,state[4],state[5]); }
            }
        }
    }
}
