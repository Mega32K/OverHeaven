package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.opengl.GL33C.*;

final class GlBloom implements AutoCloseable {
    private static final float[] EMPTY={0,0,0,65504};
    private final GlTarget[] down=new GlTarget[5], up=new GlTarget[4];
    private int levels;
    GlBloom() {
        for(int index=0;index<down.length;index++) down[index]=new GlTarget(GL_RGBA16F).linear();
        for(int index=0;index<up.length;index++) up[index]=new GlTarget(GL_RGBA16F).linear();
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
    void render(GlProgram program,int extract,int vertexArray) {
        render(program,extract,vertexArray,0,0,1,1);
    }
    void render(GlProgram program,int extract,int vertexArray,float left,float bottom,float right,float top) {
        glDisable(GL_SCISSOR_TEST); glBindVertexArray(vertexArray);
        program.use(); program.integer("Extract",extract);
        bind(down[0],left,bottom,right,top); glDrawArrays(GL_TRIANGLES,0,3);
        for(int index=1;index<levels;index++) {
            bind(down[index],left,bottom,right,top); program.integer("Extract",0); program.texture("Source",0,down[index-1].textures[0]);
            glDrawArrays(GL_TRIANGLES,0,3);
        }
        for(int index=levels-2;index>=0;index--) {
            GlTarget coarse=index==levels-2?down[index+1]:up[index+1];
            bind(up[index],left,bottom,right,top); program.integer("Extract",4); program.texture("Source",0,coarse.textures[0]);
            program.texture("Fine",4,down[index].textures[0]); program.scalar("CoarseWeight",levels-index-1);
            glDrawArrays(GL_TRIANGLES,0,3);
        }
        glDisable(GL_SCISSOR_TEST);
    }
    private static void bind(GlTarget target,float left,float bottom,float right,float top) {
        target.bind(); glDisable(GL_SCISSOR_TEST); glClearBufferfv(GL_COLOR,0,EMPTY);
        int minX=(int)Math.floor(left*target.width), minY=(int)Math.floor(bottom*target.height);
        glEnable(GL_SCISSOR_TEST); glScissor(minX,minY,(int)Math.ceil(right*target.width)-minX,(int)Math.ceil(top*target.height)-minY);
    }
    @Override public void close() {
        for(var target:down) target.close();
        for(var target:up) target.close();
        levels=0;
    }
}
