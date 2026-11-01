package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.opengl.GL33C.*;

final class GlBloom implements AutoCloseable {
    private static final float[] EMPTY={0,0,0,65504};
    private final GlTarget[] down=new GlTarget[5], up=new GlTarget[4];
    private int levels;
    private final GlRect[] downBounds=new GlRect[5],upBounds=new GlRect[4];
    GlBloom() {
        for(int index=0;index<down.length;index++) { down[index]=new GlTarget(GL_RGBA16F).linear();downBounds[index]=new GlRect(); }
        for(int index=0;index<up.length;index++) { up[index]=new GlTarget(GL_RGBA16F).linear();upBounds[index]=new GlRect(); }
    }
    static int levels(int steps) { return steps<=24?3:steps<=48?4:5; }
    static int padding(int steps) { return 12*((1<<levels(steps))-1)+8; }
    void resize(int width,int height,int steps) {
        levels=levels(steps);
        for(int index=0;index<down.length;index++) {
            if(index<levels) {
                int divisor=4<<index;
                down[index].resize(Math.max(1,(width+divisor-1)/divisor),Math.max(1,(height+divisor-1)/divisor));
                if(index<levels-1) up[index].resize(down[index].width,down[index].height);
                else if(index<up.length) up[index].close();
            } else { down[index].close(); if(index<up.length) up[index].close(); }
        }
    }
    GlTarget result() { return up[0]; }
    void bounds(GlProgram p) { p.integer("BloomBounded",upBounds[0].full(up[0].width,up[0].height)?0:1);upBounds[0].upload(p,"BloomBounds"); }
    long bytes() { long bytes=0;for(var t:down) bytes+=t.bytes();for(var t:up) bytes+=t.bytes();return bytes; }
    void render(GlProgram program,int extract,int vertexArray) {
        render(program,extract,vertexArray,0,0,1,1);
    }
    void render(GlProgram program,int extract,int vertexArray,float left,float bottom,float right,float top) {
        glDisable(GL_SCISSOR_TEST); glBindVertexArray(vertexArray);
        program.use(); program.integer("Extract",extract); program.integer("BloomBounded",1);
        bind(down[0],downBounds[0],left,bottom,right,top); glDrawArrays(GL_TRIANGLES,0,3);
        for(int index=1;index<levels;index++) {
            bind(down[index],downBounds[index],left,bottom,right,top);downBounds[index-1].upload(program,"SourceBounds");program.integer("BloomBounded",downBounds[index-1].full(down[index-1].width,down[index-1].height)?0:1); program.integer("Extract",0); program.texture("Source",0,down[index-1].textures[0]);
            glDrawArrays(GL_TRIANGLES,0,3);
        }
        for(int index=levels-2;index>=0;index--) {
            GlTarget coarse=index==levels-2?down[index+1]:up[index+1];
            bind(up[index],upBounds[index],left,bottom,right,top);
            GlRect coarseBounds=index==levels-2?downBounds[index+1]:upBounds[index+1];
            program.integer("BloomBounded",coarseBounds.full(coarse.width,coarse.height)&&downBounds[index].full(down[index].width,down[index].height)?0:1);
            coarseBounds.upload(program,"SourceBounds");downBounds[index].upload(program,"FineBounds"); program.integer("Extract",4); program.texture("Source",0,coarse.textures[0]);
            program.texture("Fine",4,down[index].textures[0]); program.scalar("CoarseWeight",levels-index-1);
            glDrawArrays(GL_TRIANGLES,0,3);
        }
        glDisable(GL_SCISSOR_TEST);
    }
    private static void bind(GlTarget target,GlRect bounds,float left,float bottom,float right,float top) {
        // Two texels cover the bilinear 3x3 kernel. Each reader also checks the logical rect.
        float px=2f/target.width,py=2f/target.height;
        bounds.normalized(left-px,bottom-py,right+px,top+py,target.width,target.height);
        target.bind();if(bounds.full(target.width,target.height)) glDisable(GL_SCISSOR_TEST);else bounds.scissor();glClearBufferfv(GL_COLOR,0,EMPTY);
    }
    @Override public void close() {
        for(var target:down) target.close();
        for(var target:up) target.close();
        levels=0;
    }
}
