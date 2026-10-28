#version 330 core
layout(location=0) out vec4 Front;
layout(location=1) out vec4 Back;
uniform vec2 GlareCenter;
uniform vec2 FullSize;
uniform float GlareDistance;
uniform sampler2D Transmission;
#include "field.glsl"
void main() {
    Front=vec4(0.);
    Back=vec4(0.);
    float footprint=max(GlareDistance*PixelAngle,.000001);
    float sampleRadius=max(.75,finaleRadius()*.095/footprint)*.8;
    for(int sampleY=-1;sampleY<=1;sampleY++) for(int sampleX=-1;sampleX<=1;sampleX++) {
        vec2 sampleUv=GlareCenter+vec2(sampleX,sampleY)*sampleRadius/FullSize;
        if(any(lessThan(sampleUv,vec2(0.))) || any(greaterThanEqual(sampleUv,vec2(1.)))) continue;
        float weight=float((sampleX==0?2:1)*(sampleY==0?2:1))/16.;
        float hit=dot(Center-Eye,rayAt(sampleUv));
        float opaque=distanceAt(sampleUv,texture(OpaqueDepth,sampleUv).r);
        if(hit<=0. || hit>=opaque) continue;
        float surface=distanceAt(sampleUv,texture(SurfaceDepth,sampleUv).r);
        if(surface<opaque-max(.002,opaque*.00001) && hit>=surface) {
            Back.rgb+=clamp(texture(Transmission,sampleUv).rgb,0.,1.)*weight;
            Back.a+=weight;
        } else Front+=vec4(weight);
    }
}
