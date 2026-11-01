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
#include "solution_bounds.glsl"
uniform ivec4 SourceBounds,FineBounds;
uniform int BloomBounded;
vec4 extractLight(vec2 uv) {
    if(!solutionContains(ivec2(floor(uv*vec2(textureSize(FrontTexture,0)))))) return vec4(0,0,0,65504);
    vec4 front=texture(FrontTexture,uv), back=texture(BackTexture,uv);
    vec2 emissionDepth=texture(LensTexture,uv).zw;
    vec3 transmission=clamp(texture(Transmission,uv).rgb,0.0,1.0);
    float opaque=distanceAt(uv,texture(OpaqueDepth,uv).r);
    float surface=distanceAt(uv,texture(SurfaceDepth,uv).r);
    vec3 direction=rayAt(uv);
    vec2 core=sphere(Eye-Center,direction,max(coreRadius(),0.000001));
    float coreHit=core.y>0.0?max(0.0,core.x):1000000.0;
    float impact=length(cross(Eye-Center,direction));
    vec3 rimColor=core.y>0. && coreHit<opaque?introHorizonRim(impact,max(coreHit,1.)*PixelAngle):vec3(0.);
    if(max(rimColor.r,max(rimColor.g,rimColor.b))>0.00001) {
        if(coreHit<surface) { front.rgb+=rimColor; emissionDepth.x=min(emissionDepth.x,coreHit); }
        else { back.rgb+=rimColor; emissionDepth.y=min(emissionDepth.y,coreHit); }
    }
    vec3 light=Extract==2?transmission*back.rgb:Extract==3?front.rgb:front.rgb+front.a*transmission*back.rgb;
    if(relativistic()) {
        float luminance=dot(light,vec3(.2126,.7152,.0722));
        // Smooth bright-pass on disk radiance only. Keep the authored singularity bloom.
        float knee=smoothstep(.3,1.5,luminance);
        light*=mix(.32*knee,1.,clamp(FinaleAmount,0.,1.));
    }
    return vec4(light,Extract==2?emissionDepth.y:Extract==3?emissionDepth.x:min(emissionDepth.x,emissionDepth.y));
}
vec4 guarded(sampler2D image,vec2 uv,float limit,ivec4 bounds) {
    vec2 size=vec2(textureSize(image,0)), coordinate=uv*size-0.5;
    ivec2 base=ivec2(floor(coordinate)); vec2 fraction=fract(coordinate);
    vec3 light=vec3(0.0); float nearest=65504.0;
    for(int offsetY=0;offsetY<2;offsetY++) for(int offsetX=0;offsetX<2;offsetX++) {
        ivec2 tap=base+ivec2(offsetX,offsetY);
        if(any(lessThan(tap,ivec2(0))) || any(greaterThanEqual(tap,ivec2(size)))) continue;
#ifndef UNBOUNDED_TARGET
        if(BloomBounded!=0 && (any(lessThan(tap,bounds.xy)) || any(greaterThanEqual(tap,bounds.zw)))) continue;
#endif
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
            vec4 sampleValue=guarded(Source,texCoord+vec2(offsetX,offsetY)*texel,limit,SourceBounds);
            light+=sampleValue.rgb*weight; nearest=min(nearest,sampleValue.a);
        }
        if(Extract==4) {
            vec4 fine=guarded(Fine,texCoord,limit,FineBounds);
            // The first upsample combines the two widest scales. Attenuate them
            // once, retaining the original denominator and all finer-scale energy.
            float gain=CoarseWeight<1.5?wideBloomGain():1.;
            light=(fine.rgb+light*CoarseWeight)*gain/(1.0+CoarseWeight); nearest=min(nearest,fine.a);
        }
    }
    Color=vec4(light,nearest);
}
