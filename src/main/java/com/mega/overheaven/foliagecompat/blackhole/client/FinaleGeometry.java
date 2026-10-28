package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.opengl.GL33C.*;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

/** One immutable position texture per show, two instanced draws, zero particle entities. */
final class FinaleGeometry implements AutoCloseable {
    private GlProgram program;
    private int positions,shapes,vao,glassCount,debrisCount,shapeCount;
    private boolean dirty=true;
    void dirty() { dirty=true; }
    void render(Matrix4f vp,Matrix4f inverse,Vector4f eye,double cameraX,double cameraY,double cameraZ,int opaque,int surface,int width,int height,float partial) {
        var frame=FinaleClient.frame(partial); int phase=frame.phase().ordinal(); if(phase<1 || phase>4) return;
        if(FinaleClient.glass().length==0 && FinaleClient.debris().length==0) return;
        try(var state=GlState.capture()) {
            if(program==null) { program=new GlProgram(GlProgram.read("finale_geometry.vsh"),GlProgram.read("finale_geometry.fsh")); vao=glGenVertexArrays(); positions=glGenTextures(); shapes=glGenTextures(); }
            if(dirty) upload();
            var hole=FinaleClient.state().hole(); program.use(); program.matrix("VP",vp); program.matrix("InverseVP",inverse); program.vector("Eye",eye.x,eye.y,eye.z);
            program.vector("Center",(float)(hole.x()-cameraX),(float)(hole.y()-cameraY),(float)(hole.z()-cameraZ));
            program.integer("Phase",phase); program.scalar("Progress",frame.progress()); program.scalar("Time",FinaleClient.ticks(partial)/20); program.scalar("Visibility",FinaleClient.stopping(partial)>=0?1-FinaleClient.stopping(partial):1); program.scalar("Seed",(hole.seed()&65535)/257.0);
            program.pair("ScreenSize",width,height); program.integer("GlassCount",glassCount); program.texture("Positions",0,positions); program.texture("Shapes",1,shapes); program.texture("OpaqueDepth",8,opaque); program.texture("SurfaceDepth",9,surface);
            boolean depthBlend=org.lwjgl.opengl.GL.getCapabilities().OpenGL40 || org.lwjgl.opengl.GL.getCapabilities().GL_ARB_draw_buffers_blend;
            glDrawBuffers(depthBlend?new int[]{GL_COLOR_ATTACHMENT0,GL_COLOR_ATTACHMENT1,GL_COLOR_ATTACHMENT2}:new int[]{GL_COLOR_ATTACHMENT0,GL_COLOR_ATTACHMENT1});
            glEnable(GL_BLEND); glBlendEquation(GL_FUNC_ADD); glBlendFuncSeparate(GL_ONE,GL_ONE,GL_ZERO,GL_ONE); glBindVertexArray(vao);
            if(depthBlend) {
                glColorMaski(2,false,false,true,true);
                if(org.lwjgl.opengl.GL.getCapabilities().OpenGL40) org.lwjgl.opengl.GL40C.glBlendEquationSeparatei(2,GL_MIN,GL_MIN);
                else org.lwjgl.opengl.ARBDrawBuffersBlend.glBlendEquationSeparateiARB(2,GL_MIN,GL_MIN);
            }
            if(glassCount>0 && (phase<2 || phase==2 && frame.progress()<.8)) { program.integer("Mode",0); glDrawArraysInstanced(GL_TRIANGLES,0,36,shapeCount); }
            if(phase>2 || phase==2 && frame.progress()>=.8) { program.integer("Mode",1); int budget=BlackHoleClient.steps==24?160:BlackHoleClient.steps==96?512:384; program.integer("GlassFragments",Math.min(budget,glassCount*3)); glDrawArraysInstanced(GL_TRIANGLES,0,6,Math.min(budget,glassCount*3)+debrisCount); }
        } finally { glDrawBuffers(new int[]{GL_COLOR_ATTACHMENT0,GL_COLOR_ATTACHMENT1,GL_COLOR_ATTACHMENT2,GL_COLOR_ATTACHMENT3}); }
    }
    private void upload() {
        long[] glass=FinaleClient.glass(),debris=FinaleClient.debris(); glassCount=glass.length; debrisCount=debris.length; int count=Math.max(1,glassCount+debrisCount); var hole=FinaleClient.state().hole();
        try(var stack=MemoryStack.stackPush()) {
            var data=stack.mallocFloat(count*4);
            for(long[] group:new long[][]{glass,debris}) for(long packed:group) { var pos=BlockPos.of(packed); data.put((float)(pos.getX()+.5-hole.x())).put((float)(pos.getY()+.5-hole.y())).put((float)(pos.getZ()+.5-hole.z())).put(group==glass?0:1); }
            data.flip(); glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D,positions);
            int unpack=glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING); glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);
            try { glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,count,1,0,GL_RGBA,GL_FLOAT,data); } finally { glBindBuffer(GL_PIXEL_UNPACK_BUFFER,unpack); }
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        }
        var level=net.minecraft.client.Minecraft.getInstance().level;
        var boxes=new java.util.ArrayList<net.minecraft.world.phys.AABB>();
        if(level!=null) for(long packed:glass) {
            var pos=BlockPos.of(packed); var state=level.getBlockState(pos);
            for(var box:state.getShape(level,pos).toAabbs()) boxes.add(box.move(pos.getX()-hole.x(),pos.getY()-hole.y(),pos.getZ()-hole.z()));
        }
        shapeCount=boxes.size();
        var data=org.lwjgl.BufferUtils.createFloatBuffer(Math.max(1,shapeCount)*8);
        for(var box:boxes) data.put((float)((box.minX+box.maxX)*.5)).put((float)((box.minY+box.maxY)*.5)).put((float)((box.minZ+box.maxZ)*.5)).put(0)
            .put((float)((box.maxX-box.minX)*.5)).put((float)((box.maxY-box.minY)*.5)).put((float)((box.maxZ-box.minZ)*.5)).put(0);
        if(boxes.isEmpty()) data.put(new float[8]); data.flip();
        glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D,shapes);
        int unpack=glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING); glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);
        try { glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,2,Math.max(1,shapeCount),0,GL_RGBA,GL_FLOAT,data); } finally { glBindBuffer(GL_PIXEL_UNPACK_BUFFER,unpack); }
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        dirty=false;
    }
    @Override public void close() { if(program!=null) program.close(); program=null; if(positions!=0) glDeleteTextures(positions); if(shapes!=0) glDeleteTextures(shapes); if(vao!=0) glDeleteVertexArrays(vao); positions=shapes=vao=0; dirty=true; }
}
