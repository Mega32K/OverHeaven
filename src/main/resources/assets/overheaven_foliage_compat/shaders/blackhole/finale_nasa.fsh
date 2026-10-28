#version 330 core
in vec2 texCoord;
out vec4 Color;
uniform sampler2D MilkyWay;
uniform mat4 InverseVP;
uniform vec3 Eye;
uniform float Seed,Extinction;
uniform int Black,Detail;
#include "finale_sky_common.glsl"
void main() {
    if(Black!=0 || Extinction>=1.) { Color=vec4(0,0,0,1);return; }
    vec4 p=InverseVP*vec4(texCoord*2.-1.,.98,1.);
    vec3 direction=cosmicRay(normalize(p.xyz/p.w-Eye));
    vec2 uv=vec2(atan(direction.z,direction.x)/6.28318530718+.5,acos(clamp(direction.y,-1.,1.))/3.14159265359);
    // Explicit angular footprint avoids the longitude seam selecting a whole-sky mip.
    float footprint=max(length(dFdx(direction)),length(dFdy(direction)));
    float latitude=max(.015,sqrt(max(0.,1.-direction.y*direction.y)));
    float lod=max(0.,log2(max(1.,footprint*4096./6.28318530718/latitude)));
    vec3 raw=textureLod(MilkyWay,uv,lod).rgb;
    vec3 light=vec3(1.)-exp(-raw*4.2);
    light*=starSurvival(direction)*(1.-smoothstep(.04,.80,Extinction));
    Color=vec4(light,1);
}
