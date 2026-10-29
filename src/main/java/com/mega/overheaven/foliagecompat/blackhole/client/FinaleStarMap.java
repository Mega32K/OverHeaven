package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.opengl.GL33C.*;
import java.io.*;
import java.nio.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;
import org.lwjgl.system.MemoryUtil;
import com.mega.overheaven.foliagecompat.PatchLog;

/** Shared all-sky source for vanilla and shader-pack finales; never replaces the host sky. */
final class FinaleStarMap implements AutoCloseable {
    @FunctionalInterface interface Source { InputStream open(String name) throws IOException; }
    private record Data(byte[] background,byte[] stars,int count) { }
    private final Source source;
    private final Function<String,String> shaders;
    private CompletableFuture<Data> loading;
    private GlProgram background,points,fallback;
    private int texture,vao,buffer,count;
    private boolean failed;
    FinaleStarMap(Source source) { this(source,GlProgram::read); }
    FinaleStarMap(Source source,Function<String,String> shaders) { this.source=source;this.shaders=shaders; }
    void prepare() {
        if(loading!=null || failed || texture!=0) return;
        loading=CompletableFuture.supplyAsync(()-> {
            try {
                byte[] bg=read("milkyway.rgb9e5.gz",16+4096*2048*4);
                ByteBuffer b=ByteBuffer.wrap(bg).order(ByteOrder.LITTLE_ENDIAN);
                if(b.getInt()!=0x574d484f || b.getInt()!=1 || b.getInt()!=4096 || b.getInt()!=2048) throw new IOException("Invalid NASA background header");
                byte[] stars=read("bright-stars.bin.gz",12+4096*28);
                b=ByteBuffer.wrap(stars).order(ByteOrder.LITTLE_ENDIAN);
                if(b.getInt()!=0x5453484f || b.getInt()!=1 || b.getInt()!=4096) throw new IOException("Invalid NASA star table");
                for(int i=12;i<stars.length;i+=4) if(!Float.isFinite(b.getFloat(i))) throw new IOException("Non-finite star record");
                return new Data(bg,stars,4096);
            } catch(IOException error) { throw new java.util.concurrent.CompletionException(error); }
        });
    }
    private byte[] read(String name,int bytes) throws IOException {
        try(var raw=source.open(name);var input=new GZIPInputStream(raw)) {
            byte[] data=input.readNBytes(bytes);
            if(data.length!=bytes || input.read()!=-1) throw new IOException("Unexpected sky resource length: "+name);
            return data;
        }
    }
    private boolean ready() {
        prepare();
        if(texture!=0) return true;
        if(failed || loading==null || !loading.isDone()) return false;
        try {
            Data data=loading.join();
            background=new GlProgram(shaders.apply("fullscreen.vsh"),shaders.apply("finale_nasa.fsh"));
            points=new GlProgram(shaders.apply("finale_star_points.vsh"),shaders.apply("finale_star_points.fsh"));
            texture=glGenTextures();glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,texture);glBindSampler(0,0);
            ByteBuffer upload=MemoryUtil.memAlloc(data.background.length-16);
            int unpack=glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING),alignment=glGetInteger(GL_UNPACK_ALIGNMENT),rowLength=glGetInteger(GL_UNPACK_ROW_LENGTH),skipRows=glGetInteger(GL_UNPACK_SKIP_ROWS),skipPixels=glGetInteger(GL_UNPACK_SKIP_PIXELS);
            boolean swapBytes=glGetBoolean(GL_UNPACK_SWAP_BYTES);
            try {
                glPixelStorei(GL_UNPACK_SWAP_BYTES,GL_FALSE);glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);glPixelStorei(GL_UNPACK_ALIGNMENT,4);glPixelStorei(GL_UNPACK_ROW_LENGTH,0);glPixelStorei(GL_UNPACK_SKIP_ROWS,0);glPixelStorei(GL_UNPACK_SKIP_PIXELS,0);
                upload.put(data.background,16,data.background.length-16).flip();glTexImage2D(GL_TEXTURE_2D,0,GL_RGB9_E5,4096,2048,0,GL_RGB,GL_UNSIGNED_INT_5_9_9_9_REV,upload);
            } finally {
                glPixelStorei(GL_UNPACK_SWAP_BYTES,swapBytes?GL_TRUE:GL_FALSE);glBindBuffer(GL_PIXEL_UNPACK_BUFFER,unpack);glPixelStorei(GL_UNPACK_ALIGNMENT,alignment);glPixelStorei(GL_UNPACK_ROW_LENGTH,rowLength);glPixelStorei(GL_UNPACK_SKIP_ROWS,skipRows);glPixelStorei(GL_UNPACK_SKIP_PIXELS,skipPixels);MemoryUtil.memFree(upload);
            }
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR_MIPMAP_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_REPEAT);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);glGenerateMipmap(GL_TEXTURE_2D);
            vao=glGenVertexArrays();buffer=glGenBuffers();glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,buffer);
            upload=MemoryUtil.memAlloc(data.stars.length-12);
            try { upload.put(data.stars,12,data.stars.length-12).flip();glBufferData(GL_ARRAY_BUFFER,upload,GL_STATIC_DRAW); }
            finally { MemoryUtil.memFree(upload); }
            for(int attribute=0;attribute<3;attribute++) { glEnableVertexAttribArray(attribute);glVertexAttribPointer(attribute,attribute==2?1:3,GL_FLOAT,false,28,attribute==0?0:attribute==1?12:24);glVertexAttribDivisor(attribute,1); }
            count=data.count;loading=null;
            return true;
        } catch(RuntimeException error) {
            release();loading=null;failed=true;
            PatchLog.LOGGER.error("NASA finale sky unavailable; using procedural short-trail fallback until resource reload",error);
            return false;
        }
    }
    boolean drawBackground(Consumer<GlProgram> uniforms,int fullscreenVao) {
        boolean nasa=ready();
        if(!nasa && fallback==null) fallback=new GlProgram(shaders.apply("fullscreen.vsh"),shaders.apply("finale_stars.fsh"));
        GlProgram p=nasa?background:fallback;p.use();uniforms.accept(p);
        if(nasa) p.texture("MilkyWay",0,texture);
        glBindVertexArray(fullscreenVao);glDrawArrays(GL_TRIANGLES,0,3);return nasa;
    }
    void drawStars(Consumer<GlProgram> uniforms,int width,int height,int detail) {
        if(texture==0 || points==null) return;
        try(var state=GlState.capture()) {
            GlState.fullscreen();points.use();uniforms.accept(points);points.pair("FullSize",width,height);
            glEnable(GL_BLEND);glBlendEquation(GL_FUNC_ADD);glBlendFuncSeparate(GL_ONE,GL_ONE,GL_ZERO,GL_ONE);
            int visible=Math.min(count,detail==0?1024:detail==2?4096:2048),segments=detail==0?4:detail==2?12:8;
            points.integer("TrailSegments",segments);points.integer("BrightStars",visible/4);
            glBindVertexArray(vao);glDrawArraysInstanced(GL_TRIANGLES,0,6*(segments+1),visible);
        }
    }
    void warm() {
        prepare();
        if(loading!=null && loading.isDone()) try(var state=GlState.capture()) { ready(); }
    }
    boolean loaded() { return texture!=0; }
    boolean loading() { return loading!=null && !loading.isDone(); }
    String path() { return texture!=0?"nasa-2020-full-sphere":failed?"procedural-short-trails":"nasa-loading"; }
    private void release() {
        if(background!=null) background.close();if(points!=null) points.close();if(fallback!=null) fallback.close();background=points=fallback=null;
        if(texture!=0) glDeleteTextures(texture);if(buffer!=0) glDeleteBuffers(buffer);if(vao!=0) glDeleteVertexArrays(vao);texture=buffer=vao=count=0;
    }
    @Override public void close() { if(loading!=null) loading.cancel(false);loading=null;release();failed=false; }
}
