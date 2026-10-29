#version 330 core
in vec2 texCoord;
out vec4 Color;
uniform mat4 InverseVP;
uniform vec3 Eye,Center;
uniform float Time,Seed,Extinction,Progress;
uniform int Phase,Detail,Black;
#include "finale_cosmos.glsl"
#include "finale_star_style.glsl"
vec3 starHash(vec3 position) {
    position=fract(position*vec3(.1031,.1030,.0973)); position+=dot(position,position.yxz+33.33+Seed*.013);
    return fract((position.xxy+position.yxx)*position.zyx);
}
void main() {
    if(Black!=0 || Extinction>=1.) { Color=vec4(0,0,0,1); return; }
    vec4 point=InverseVP*vec4(texCoord*2.-1.,.98,1.);
    vec3 direction=normalize(point.xyz/point.w-Eye),ray=cosmicRay(direction);
    vec3 tangent=normalize(cross(cosmicAxis(),ray)+vec3(.00001));
    float scale=Detail==0?62.:85.; vec3 base=floor(ray*scale),light=vec3(0);
    float footprint=max(length(dFdx(ray)),length(dFdy(ray))),trail=cosmicTrail();
    for(int offsetZ=-1;offsetZ<=1;offsetZ++) for(int offsetY=-1;offsetY<=1;offsetY++) for(int offsetX=-1;offsetX<=1;offsetX++) {
        vec3 cell=base+vec3(offsetX,offsetY,offsetZ),random=starHash(cell);
        if(random.z<.94) continue;
        vec3 star=normalize((cell+.15+random*.7)/scale),delta=star-ray;
        float along=dot(delta,tangent),across=length(delta-tangent*along);
        float width=max(.00032+random.x*.00044,footprint*.62);
        float core=exp(-dot(delta,delta)/(width*width));
        float shape=exp(-across*across/(width*width))*exp(-along*along/(trail*trail+width*width));
        float flux=width/max(width,trail*.27),survival=starSurvival(star);
        vec3 tint=dreamTint(random.y);
        float tailGate=dreamTailGate(trail)*smoothstep(0.,.025,ExposureSeconds);
        vec3 radiance=mix(vec3(1.),tint,.14)*core*1.6;
        radiance+=mix(vec3(1.),tint,.32)*shape*flux*1.8*dreamArcGain(trail)*tailGate;
        if(random.x>.97) radiance+=tint*.065*dreamHalo(length(delta),min(.009,width*6.));
        light+=radiance*(.6+random.x*1.15)*survival;
    }
    for(int island=0;island<3;island++) {
        vec3 center=normalize(island==0?vec3(.25,.24,-1):island==1?vec3(-.6,.45,-.8):vec3(.7,-.25,.8));
        for(int member=0;member<5;member++) {
            vec3 offset=(starHash(vec3(island,member,9))-.5)*.065;
            vec3 star=normalize(center+offset),delta=star-ray;
            float alive=starSurvival(star);
            float along=dot(delta,tangent),across=length(delta-tangent*along),width=max(.0006,footprint*.6);
            float head=exp(-dot(delta,delta)/(width*width))*1.6;
            float tail=exp(-across*across/(width*width))*exp(-along*along/(width*width+trail*trail))*1.8*width/max(width,trail*.25)*dreamTailGate(trail);
            light+=vec3(.88,.92,1)*(head+tail)*alive*.7;
        }
    }
    if(Extinction>.001) {
        float threshold=skyDissolveThreshold(ray);
        light*=skyDissolveCoverage(threshold,fwidth(threshold));
    }
    Color=vec4(max(light+cosmicScenery(direction),vec3(0)),1);
}
