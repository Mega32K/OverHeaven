#version 330 core
in vec2 texCoord;
out vec4 Color;
uniform sampler2D Scene,Cosmos;
uniform float Coverage,Flash,Pulse,CameraExposure,CameraChroma;
uniform vec2 FullSize;
uniform int Black;
vec3 sceneAt(vec2 uv) {
    if(Coverage<=0.) return texture(Scene,uv).rgb;
    if(Coverage>=1.) return texture(Cosmos,uv).rgb;
    return mix(texture(Scene,uv).rgb,texture(Cosmos,uv).rgb,Coverage);
}
void main() {
    if(Black!=0) { Color=vec4(0,0,0,1); return; }
    if(Flash>=1.) { Color=vec4(1); return; }
    vec3 light=max(sceneAt(texCoord),vec3(0));
    if(CameraChroma>0. && min(FullSize.x,FullSize.y)>0.) {
        vec2 p=(texCoord-.5)*FullSize;
        float r=length(p)/max(1.,length(FullSize*.5));
        vec2 shift=normalize(p+vec2(1.e-8))*pow(r,1.7)*8.*min(FullSize.x,FullSize.y)/1080.*CameraChroma/FullSize;
        vec2 halfPixel=.5/FullSize;
        // Finite lens response at the image border; no clamped streaks.
        float border=min(min(texCoord.x,texCoord.y),min(1.-texCoord.x,1.-texCoord.y));
        shift*=smoothstep(0.,max(abs(shift.x),abs(shift.y))+.002,border);
        light.r=sceneAt(clamp(texCoord+shift,halfPixel,1.-halfPixel)).r;
        light.b=sceneAt(clamp(texCoord-shift,halfPixel,1.-halfPixel)).b;
    }
    // The host has already performed its display transform. A bounded exposure response
    // raises highlights without treating this display-space scene as raw HDR.
    if(CameraExposure>0.) light=mix(vec3(1.)-pow(max(vec3(0),vec3(1.)-light),vec3(exp2(CameraExposure))),light*exp2(CameraExposure),step(vec3(1.),light));
    light+=(vec3(1)-exp(-light*2.))*Pulse*.06;
    Color=vec4(mix(light,vec3(1),clamp(Flash,0.,1.)),1);
}
