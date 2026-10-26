package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.opengl.GL33C.*;

final class GlTarget implements AutoCloseable {
    private final int[] formats;
    private final int[] buffers;
    private boolean linear;
    final int[] textures;
    int framebuffer, width, height;
    GlTarget(int... formats) {
        this.formats = formats; textures = new int[formats.length]; buffers = new int[formats.length];
        for (int index = 0; index < formats.length; index++) buffers[index] = GL_COLOR_ATTACHMENT0 + index;
    }
    GlTarget linear() { linear=true; return this; }
    void resize(int nextWidth, int nextHeight) {
        if (framebuffer != 0 && width == nextWidth && height == nextHeight) return;
        close(); width = nextWidth; height = nextHeight; framebuffer = glGenFramebuffers(); glBindFramebuffer(GL_FRAMEBUFFER, framebuffer); glActiveTexture(GL_TEXTURE0);
        for (int index = 0; index < textures.length; index++) {
            textures[index] = glGenTextures(); glBindTexture(GL_TEXTURE_2D, textures[index]);
            int unpackBuffer=glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);
            try { glTexImage2D(GL_TEXTURE_2D, 0, formats[index], width, height, 0, formats[index] == GL_R32F ? GL_RED : GL_RGBA, GL_FLOAT, 0L); }
            finally { glBindBuffer(GL_PIXEL_UNPACK_BUFFER,unpackBuffer); }
            int filter=linear && formats[index]!=GL_R32F?GL_LINEAR:GL_NEAREST;
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glFramebufferTexture2D(GL_FRAMEBUFFER, buffers[index], GL_TEXTURE_2D, textures[index], 0);
        }
        glDrawBuffers(buffers);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) { close(); throw new IllegalStateException("Black hole framebuffer incomplete"); }
    }
    void bind() { glBindFramebuffer(GL_FRAMEBUFFER, framebuffer); glViewport(0, 0, width, height); }
    @Override public void close() {
        for (int index = 0; index < textures.length; index++) { if (textures[index] != 0) glDeleteTextures(textures[index]); textures[index] = 0; }
        if (framebuffer != 0) glDeleteFramebuffers(framebuffer); framebuffer = 0; width = 0; height = 0;
    }
}
