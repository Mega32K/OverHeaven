#version 330 core
in vec2 texCoord;
out vec4 Color;
uniform mat4 InverseVP;
uniform vec3 Eye,Center;
uniform float Time,Seed,Extinction,Progress;
uniform int Phase,Detail,Black;
// PACK_OPTIONS
float max0(float v) { return max(v,0.); }
float min1(float v) { return min(v,1.); }
float pow2(float v) { return v*v; }
vec3 clamp01(vec3 v) { return clamp(v,0.,1.); }
float GetLuminance(vec3 v) { return dot(v,vec3(.299,.587,.114)); }
// The finale is outside the atmosphere. Keep the selected star density, but use night visibility
// independently of world daytime/rain. No world time, rain or user option is modified.
const float invRainFactor=1.,invNoonFactor2=1.,sunVisibility=0.;
const mat4 gbufferModelViewInverse=mat4(1.);
float syncedTime=Time;
// PACK_STARS
// PACK_TONEMAP
#include "finale_cosmos.glsl"
vec3 packStars(vec3 ray) { return GetStars(GetFullSphereStarCoord(ray),ray.y,0.); }
void main() {
    if(Black!=0 || Extinction>=1.) { Color=vec4(0,0,0,1); return; }
    vec4 point=InverseVP*vec4(texCoord*2.-1.,.98,1.);
    vec3 direction=normalize(point.xyz/point.w-Eye),ray=cosmicRay(direction);
    vec3 derivativeX=dFdx(ray),derivativeY=dFdy(ray),tangent=normalize(cross(cosmicAxis(),ray)+vec3(.00001));
    vec3 light=vec3(0); int taps=Detail==0?3:Detail==1?5:7;
    float trail=cosmicTrail();
    for(int sampleIndex=0;sampleIndex<7;sampleIndex++) {
        if(sampleIndex>=taps) break;
        float shutter=(float(sampleIndex)+.5)/float(taps)-.5;
        vec3 footprint=derivativeX*(fract(float(sampleIndex)*.618)-.5)+derivativeY*(fract(float(sampleIndex)*.382)-.5);
        vec3 tap=normalize(ray+tangent*shutter*trail*3.+footprint*.5);
        light+=packStars(tap)*starSurvival(tap)/float(taps);
    }
    DoCompTonemap(light); DoBSLColorSaturation(light);
    Color=vec4(max(light,vec3(0))+cosmicScenery(direction),1);
}
