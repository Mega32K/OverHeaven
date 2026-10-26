package com.mega.overheaven.foliagecompat.blackhole.client;

import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Reused conservative screen rectangle; no per-frame allocations. */
final class BlackHoleScreenBounds {
    int left,bottom,right,top;
    private final Vector4f point=new Vector4f();
    boolean project(Matrix4f vp,Vector4f eye,double x,double y,double z,double extent,int width,int height) {
        double dx=x-eye.x,dy=y-eye.y,dz=z-eye.z;
        if(dx*dx+dy*dy+dz*dz<=extent*extent) {
            left=bottom=0; right=width; top=height; return true;
        }
        float minX=Float.POSITIVE_INFINITY,minY=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX;
        boolean behind=false,ahead=false,outsideFar=true,outsideNear=true;
        for(int corner=0;corner<8;corner++) {
            point.set((float)(x+((corner&1)==0?-extent:extent)),(float)(y+((corner&2)==0?-extent:extent)),(float)(z+((corner&4)==0?-extent:extent)),1);
            vp.transform(point);
            if(point.z<=point.w) outsideFar=false;
            if(point.z>=-point.w) outsideNear=false;
            if(point.w<=0.01f) { behind=true; continue; }
            ahead=true;
            float horizontal=point.x/point.w,vertical=point.y/point.w;
            minX=Math.min(minX,horizontal); maxX=Math.max(maxX,horizontal);
            minY=Math.min(minY,vertical); maxY=Math.max(maxY,vertical);
        }
        if(!ahead || outsideFar || outsideNear) return false;
        if(behind) { minX=minY=-1; maxX=maxY=1; }
        if(maxX < -1 || minX>1 || maxY < -1 || minY>1) return false;
        left=Math.max(0,(int)((minX*0.5+0.5)*width)-2);
        bottom=Math.max(0,(int)((minY*0.5+0.5)*height)-2);
        right=Math.min(width,(int)Math.ceil((maxX*0.5+0.5)*width)+2);
        top=Math.min(height,(int)Math.ceil((maxY*0.5+0.5)*height)+2);
        return right-left>=2 && top-bottom>=2;
    }
}
