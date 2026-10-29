#version 330 core
layout(location=0) in vec3 StarDirection;
layout(location=1) in vec3 StarColor;
layout(location=2) in float StarFlux;
uniform mat4 VP;
uniform vec2 FullSize;
uniform float Seed,Extinction;
uniform int Black,Detail,TrailSegments,BrightStars;
noperspective out vec2 Local;
flat out float Width,Length,Flux,SoftWidth,HaloRadius,HaloFlux;
flat out vec3 Tint,SoftTint;
flat out vec2 Shutter;
#include "finale_sky_common.glsl"
#include "finale_star_style.glsl"
bool clipPlane(float a,float b,inout float low,inout float high) {
    if(a<0. && b<0.) return false;
    if(a<0.) low=max(low,a/(a-b));
    if(b<0.) high=min(high,a/(a-b));
    return low<=high;
}
float exposureAt(float time) {
    float at=clamp(time,0.,1.)*64.;int i=min(63,int(at));
    return mix(ExposureAngles[i],ExposureAngles[i+1],at-float(i));
}
float travelAt(float time) {
    float at=clamp(time,0.,1.)*64.;int i=min(63,int(at));
    return mix(ExposureTravel[i],ExposureTravel[i+1],at-float(i));
}
void main() {
    gl_Position=vec4(2,2,2,1);Local=vec2(0);Width=1.;Length=Flux=SoftWidth=HaloRadius=HaloFlux=0.;Tint=SoftTint=vec3(0);Shutter=vec2(0);
    if(Black!=0 || Extinction>=1. || starSurvival(StarDirection)<=0.) return;
    int part=gl_VertexID/6;
    bool exposed=ExposureEnabled!=0 && TrailSegments>0;
    if(part>0 && !exposed) return;
    float pixelScale=min(FullSize.x,FullSize.y)/1080.;
    Width=max(.62,pixelScale*(.62+StarFlux*.18));
    float sigma=sqrt(Width*Width+.25);
    vec2 corners[6]=vec2[6](vec2(-1,-1),vec2(1,-1),vec2(1,1),vec2(-1,-1),vec2(1,1),vec2(-1,1));
    vec2 corner=corners[gl_VertexID%6];
    float identity=dreamIdentity(StarDirection);
    SoftTint=dreamTint(identity);Tint=mix(vec3(1.),SoftTint,.14);
    if(part==0) {
        vec4 center=VP*vec4(cosmicRotate(StarDirection,cosmicAxis(),OrbitAngle),0.);
        if(center.w<=.00001) return;
        if(gl_InstanceID<64) {
            HaloRadius=pixelScale*mix(4.,12.,smoothstep(.65,2.78,StarFlux));
            HaloFlux=StarFlux*.065;
        }
        Local=corner*max(sigma*3.5,HaloRadius);Flux=StarFlux*1.6;
        gl_Position=vec4(center.xy/center.w+Local*2./FullSize,.9999,1.);return;
    }
    int segment=part-1;
    bool bright=gl_InstanceID<BrightStars;
    float shutter=bright?1.:mix(.55,.85,identity);
    float arc=travelAt(shutter)*length(cross(StarDirection,cosmicAxis()));
    float gate=dreamTailGate(arc);if(gate<=0.) return;
    float a=exposureAt(shutter*float(segment)/float(TrailSegments));
    float b=exposureAt(shutter*float(segment+1)/float(TrailSegments));
    vec4 c0=VP*vec4(cosmicRotate(StarDirection,cosmicAxis(),OrbitAngle+a),0.);
    vec4 c1=VP*vec4(cosmicRotate(StarDirection,cosmicAxis(),OrbitAngle+b),0.);
    SoftWidth=bright?sigma*2.4:0.;Tint=mix(vec3(1.),SoftTint,.32);
    float support=max(sigma,SoftWidth)*3.5;
    // The same finite support is used for clipping and raster geometry.
    float low=0.,high=1.;vec2 margin=vec2(1.)+support*2./FullSize;
    if(!clipPlane(c0.w-.0001,c1.w-.0001,low,high)
        || !clipPlane(c0.x+margin.x*c0.w,c1.x+margin.x*c1.w,low,high)
        || !clipPlane(margin.x*c0.w-c0.x,margin.x*c1.w-c1.x,low,high)
        || !clipPlane(c0.y+margin.y*c0.w,c1.y+margin.y*c1.w,low,high)
        || !clipPlane(margin.y*c0.w-c0.y,margin.y*c1.w-c1.y,low,high)) return;
    vec4 start=mix(c0,c1,low),end=mix(c0,c1,high);
    vec2 p0=start.xy/start.w*FullSize*.5,p1=end.xy/end.w*FullSize*.5;
    vec2 delta=p1-p0;Length=length(delta);
    vec2 along=Length>.0001?delta/Length:vec2(1,0),across=vec2(-along.y,along.x);
    float count=float(TrailSegments),t0=(float(segment)+low)/count,t1=(float(segment)+high)/count;
    float mass=dreamShutterIntegral(t1)-dreamShutterIntegral(t0);
    Shutter=vec2(t0,t1);
    Flux=StarFlux*.6*1.8*dreamArcGain(arc)*gate*mass;
    Local=vec2(mix(-support,Length+support,corner.x*.5+.5),corner.y*support);
    gl_Position=vec4((p0+along*Local.x+across*Local.y)*2./FullSize,.9999,1.);
}
