#include "solution_bounds.glsl"
void radianceTap(ivec2 tap,float spatial,float opaque,float surface,bool captured,float inverseTolerance,inout vec4 front,inout vec4 back,inout float total) {
    ivec2 size=textureSize(FrontTexture,0);
#ifdef UNBOUNDED_TARGET
    tap=clamp(tap,ivec2(0),size-1);
#else
    if(any(lessThan(tap,ivec2(0))) || any(greaterThanEqual(tap,size)) || !solutionContains(tap)) return;
#endif
    vec4 meta=texelFetch(MetaTexture,tap,0);
    if((meta.z>0.5)!=captured) return;
    float weight=spatial*exp(-(abs(meta.x-opaque)+abs(meta.y-surface))*inverseTolerance);
    if(weight<.000001) return;
    front+=texelFetch(FrontTexture,tap,0)*weight;
    back+=texelFetch(BackTexture,tap,0)*weight;
    total+=weight;
}
void reconstructRadiance(vec2 uv,float opaque,float surface,bool captured,out Field f,out float total) {
    f.front=vec4(0); f.back=vec4(0); f.lens=vec4(uv,65504,65504); f.meta=vec4(opaque,surface,captured?1:0,-1);
    vec2 coordinate=uv*vec2(textureSize(FrontTexture,0))-0.5;
    ivec2 base=ivec2(floor(coordinate)); vec2 fraction=fract(coordinate);total=0.;
    float inverseTolerance=1./max(Radius*.08,min(opaque,1000.)*.001);
    for(int y=0;y<2;y++) for(int x=0;x<2;x++)
        radianceTap(base+ivec2(x,y),(x==0?1.-fraction.x:fraction.x)*(y==0?1.-fraction.y:fraction.y),opaque,surface,captured,inverseTolerance,f.front,f.back,total);
    if(total<.01) {
        for(int y=-1;y<=2;y++) for(int x=-1;x<=2;x++) {
            if(x>=0 && x<2 && y>=0 && y<2) continue;
            vec2 delta=vec2(x,y)-fraction;
            radianceTap(base+ivec2(x,y),.125*exp(-dot(delta,delta)*.5),opaque,surface,captured,inverseTolerance,f.front,f.back,total);
        }
    }
    if(total>=.01) { f.front/=total;f.back/=total; }
}
