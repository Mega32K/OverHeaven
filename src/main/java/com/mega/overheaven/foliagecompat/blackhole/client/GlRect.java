package com.mega.overheaven.foliagecompat.blackhole.client;
import static org.lwjgl.opengl.GL33C.*;
final class GlRect {
    int left,bottom,right,top;
    void set(int l,int b,int r,int t) { left=l;bottom=b;right=r;top=t; }
    void normalized(float l,float b,float r,float t,int width,int height) {
        set(Math.max(0,(int)Math.floor(l*width)),Math.max(0,(int)Math.floor(b*height)),Math.min(width,(int)Math.ceil(r*width)),Math.min(height,(int)Math.ceil(t*height)));
    }
    void scissor() { glEnable(GL_SCISSOR_TEST);glScissor(left,bottom,Math.max(0,right-left),Math.max(0,top-bottom)); }
    void upload(GlProgram p,String name) { p.rectangle(name,left,bottom,right,top); }
    boolean full(int width,int height) { return left==0 && bottom==0 && right==width && top==height; }
    long area() { return (long)Math.max(0,right-left)*Math.max(0,top-bottom); }
}
