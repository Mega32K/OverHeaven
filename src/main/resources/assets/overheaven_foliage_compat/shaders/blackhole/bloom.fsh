#version 330 core
in vec2 texCoord;
out vec4 Color;
uniform sampler2D Source;
uniform sampler2D Fine;
uniform sampler2D FrontTexture;
uniform sampler2D BackTexture;
uniform sampler2D Transmission;
uniform sampler2D LensTexture;
uniform int Extract;
uniform float CoarseWeight;
uniform vec2 FullSize;
#include "field.glsl"
vec4 extractLight(vec2 uv) {
    vec4 front=texture(FrontTexture,uv), back=texture(BackTexture,uv);
    vec2 emissionDepth=texture(LensTexture,uv).zw;
    vec3 transmission=clamp(texture(Transmission,uv).rgb,0.0,1.0);
    float opaque=distanceAt(uv,texture(OpaqueDepth,uv).r);
    float surface=distanceAt(uv,texture(SurfaceDepth,uv).r);
    vec3 direction=rayAt(uv);
    vec2 core=sphere(Eye-Center,direction,max(coreRadius(),0.000001));
    float coreHit=core.y>0.0?max(0.0,core.x):1000000.0;
    float impact=length(cross(Eye-Center,direction));
    float rimWidth=max(max(coreHit,1.0)*PixelAngle*1.3,Radius*0.003);
    float rim=FinaleAmount<0.99999 && coreHit<opaque?exp(-abs(impact-Radius)/rimWidth)*Brightness*0.18:0.0;
    if(rim>0.00001) {
        vec3 rimColor=innerTransitionColor(HotColor,Radius)*rim;
        if(coreHit<surface) { front.rgb+=rimColor; emissionDepth.x=min(emissionDepth.x,coreHit); }
        else { back.rgb+=rimColor; emissionDepth.y=min(emissionDepth.y,coreHit); }
    }
    if(Extract==2) return vec4(transmission*back.rgb,emissionDepth.y);
    if(Extract==3) return vec4(front.rgb,emissionDepth.x);
    return vec4(front.rgb+front.a*transmission*back.rgb,min(emissionDepth.x,emissionDepth.y));
}
vec4 guarded(sampler2D image,vec2 uv,float limit) {
    vec2 size=vec2(textureSize(image,0)), coordinate=uv*size-0.5;
    ivec2 base=ivec2(floor(coordinate)); vec2 fraction=fract(coordinate);
    vec3 light=vec3(0.0); float nearest=65504.0;
    for(int offsetY=0;offsetY<2;offsetY++) for(int offsetX=0;offsetX<2;offsetX++) {
        ivec2 tap=base+ivec2(offsetX,offsetY);
        if(any(lessThan(tap,ivec2(0))) || any(greaterThanEqual(tap,ivec2(size)))) continue;
        vec4 sampleValue=texelFetch(image,tap,0);
        float weight=(offsetX==0?1.0-fraction.x:fraction.x)*(offsetY==0?1.0-fraction.y:fraction.y);
        if(sampleValue.a<limit && sampleValue.a<65500.0 && max(sampleValue.r,max(sampleValue.g,sampleValue.b))>0.00001) {
            light+=sampleValue.rgb*weight; nearest=min(nearest,sampleValue.a);
        }
    }
    return vec4(light,nearest);
}
void main() {
    float limit=distanceAt(texCoord,texture(OpaqueDepth,texCoord).r);
    vec3 light=vec3(0.0); float nearest=65504.0;
    if(Extract>=1 && Extract<=3) {
        for(int offsetY=0;offsetY<4;offsetY++) for(int offsetX=0;offsetX<4;offsetX++) {
            vec2 uv=texCoord+(vec2(offsetX,offsetY)-1.5)/FullSize;
            vec4 sourceValue=extractLight(uv);
            float sourceLimit=distanceAt(uv,texture(OpaqueDepth,uv).r);
            if(sourceValue.a<min(limit,sourceLimit) && sourceValue.a<65500.0 && max(sourceValue.r,max(sourceValue.g,sourceValue.b))>0.00001) {
                light+=sourceValue.rgb/16.0; nearest=min(nearest,sourceValue.a);
            }
        }
    } else {
        vec2 texel=1.0/vec2(textureSize(Source,0));
        for(int offsetY=-1;offsetY<=1;offsetY++) for(int offsetX=-1;offsetX<=1;offsetX++) {
            float weight=float((offsetX==0?2:1)*(offsetY==0?2:1))/16.0;
            vec4 sampleValue=guarded(Source,texCoord+vec2(offsetX,offsetY)*texel,limit);
            light+=sampleValue.rgb*weight; nearest=min(nearest,sampleValue.a);
        }
        if(Extract==4) {
            vec4 fine=guarded(Fine,texCoord,limit);
            light=(fine.rgb+light*CoarseWeight)/(1.0+CoarseWeight); nearest=min(nearest,fine.a);
        }
    }
    Color=vec4(light,nearest);
}
