#version 330 core
layout(location=0) in vec3 StarDirection;
layout(location=1) in vec3 StarColor;
layout(location=2) in float StarFlux;
uniform mat4 VP;
uniform vec2 FullSize;
uniform float Seed,Extinction;
uniform int Black,Detail;
out vec2 Local;
flat out float Width,Trail,Flux;
flat out vec3 Tint;
#include "finale_sky_common.glsl"
void main() {
    vec3 direction=cosmicRotate(StarDirection,cosmicAxis(),OrbitAngle);
    vec4 center=VP*vec4(direction,0.);
    if(center.w<=.00001 || Black!=0 || Extinction>=1.) { gl_Position=vec4(2,2,2,1);Local=vec2(0);Width=1.;Trail=Flux=0.;Tint=vec3(0);return; }
    float alive=starSurvival(StarDirection);
    float scale=min(FullSize.x,FullSize.y)/1080.;
    Width=max(.62,scale*(.62+StarFlux*.18));
    vec3 tangent=normalize(cross(cosmicAxis(),direction)+vec3(.00001));
    vec4 next=VP*vec4(normalize(direction+tangent*cosmicTrail()),0.);
    vec2 motion=next.w>.00001?(next.xy/next.w-center.xy/center.w)*FullSize*.5:vec2(0);
    Trail=min(length(motion),min(FullSize.x,FullSize.y)*.035);
    vec2 along=length(motion)>.001?normalize(motion):vec2(1,0),across=vec2(-along.y,along.x);
    vec2 corners[6]=vec2[6](vec2(-1,-1),vec2(1,-1),vec2(1,1),vec2(-1,-1),vec2(1,1),vec2(-1,1));
    Local=corners[gl_VertexID]*vec2(Trail+Width*3.5,Width*3.5);
    vec2 pixel=along*Local.x+across*Local.y;
    gl_Position=center;gl_Position.z=center.w*.9999;gl_Position.xy+=pixel*2./FullSize*center.w;
    Flux=StarFlux*alive*Width/max(Width,Trail*.27);Tint=StarColor;
}
