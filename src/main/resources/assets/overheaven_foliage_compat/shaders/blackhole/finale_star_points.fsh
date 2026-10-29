#version 330 core
noperspective in vec2 Local;
flat in float Width,Length,Flux,SoftWidth,HaloRadius,HaloFlux;
flat in vec3 Tint,SoftTint;
flat in vec2 Shutter;
out vec4 Color;
uniform mat4 InverseVP;
uniform vec3 Eye;
uniform vec2 FullSize;
uniform float Seed,Extinction;
uniform int Detail;
#include "finale_sky_common.glsl"
#include "finale_star_style.glsl"
float gaussianIntegral(float x) {
    float s=sign(x);x=abs(x);float t=1./(1.+.3275911*x);
    return s*(1.-(((((1.061405429*t-1.453152027)*t)+1.421413741)*t-.284496736)*t+.254829592)*t*exp(-x*x));
}
float lineKernel(float sigma) {
    float along=Length<.01?exp(-pow((Local.x-Length*.5)/sigma,2.)):
        max(0.,.886226925*sigma/Length*(gaussianIntegral(Local.x/sigma)-gaussianIntegral((Local.x-Length)/sigma)));
    return exp(-Local.y*Local.y/(sigma*sigma))*along;
}
void main() {
    float sigma=sqrt(Width*Width+.25);
    // Correct the piecewise mean to the continuous shutter profile at joins.
    // Its interval integral remains the segment's budget, independent of count.
    float correction=1.;
    if(Length>.01 && Shutter.y>Shutter.x) {
        float mass=dreamShutterIntegral(Shutter.y)-dreamShutterIntegral(Shutter.x);
        float time=mix(Shutter.x,Shutter.y,clamp(Local.x/Length,0.,1.));
        correction=dreamShutterWeight(time)*(Shutter.y-Shutter.x)/max(mass,.0000001);
    }
    vec3 light=Tint*lineKernel(sigma)*Flux*correction;
    if(SoftWidth>0.) light+=SoftTint*lineKernel(SoftWidth)*Flux*correction*.35*pow(sigma/SoftWidth,2.);
    if(HaloRadius>0.) light+=SoftTint*HaloFlux*dreamHalo(length(Local),HaloRadius);
    float alive=1.;
    if(Extinction>.001) {
        vec4 p=InverseVP*vec4(gl_FragCoord.xy/FullSize*2.-1.,.98,1.);
        vec3 ray=cosmicRay(normalize(p.xyz/p.w-Eye));
        float threshold=skyDissolveThreshold(ray);
        alive=skyDissolveCoverage(threshold,fwidth(threshold));
    }
    Color=vec4(light*alive,0);
}
