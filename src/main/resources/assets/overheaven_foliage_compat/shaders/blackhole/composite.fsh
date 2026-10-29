#version 330 core
in vec2 texCoord;
layout(location=0) out vec4 Color;
uniform sampler2D Scene;
uniform sampler2D Opaque;
uniform sampler2D Transmission;
uniform sampler2D FrontTexture;
uniform sampler2D BackTexture;
uniform sampler2D LensTexture;
uniform sampler2D MetaTexture;
uniform sampler2D Bloom;
uniform int DisplaySpace;
uniform int LayerMode;
#include "field.glsl"
#include "singularity_glare.glsl"
vec3 bloomAt(vec2 uv,float limit) {
    if(Glow<=0.0) return vec3(0.0);
    vec2 size=vec2(textureSize(Bloom,0)), coordinate=uv*size-0.5;
    ivec2 base=ivec2(floor(coordinate)); vec2 fraction=fract(coordinate);
    vec3 light=vec3(0.0);
    for(int offsetY=0;offsetY<2;offsetY++) for(int offsetX=0;offsetX<2;offsetX++) {
        ivec2 tap=clamp(base+ivec2(offsetX,offsetY),ivec2(0),ivec2(size)-1);
        vec4 sampleValue=texelFetch(Bloom,tap,0);
        float weight=(offsetX==0?1.0-fraction.x:fraction.x)*(offsetY==0?1.0-fraction.y:fraction.y);
        if(sampleValue.a<limit && sampleValue.a<65500.0) light+=sampleValue.rgb*weight;
    }
    return light*Glow*mix(1.0,2.2,clamp(FinaleAmount,0.0,1.0));
}
vec3 backgroundSample(vec2 uv, vec2 warped, float minimumDistance, float confidence, bool completeScene, vec3 original,out float accepted) {
    accepted=0.;
    if (minimumDistance < 0.0 || confidence <= 0.0) return original;
    vec2 size = vec2(textureSize(OpaqueDepth,0));
    vec2 halfPixel = 0.5 / size;
    float border = min(min(warped.x,warped.y),min(1.0-warped.x,1.0-warped.y));
    confidence *= smoothstep(max(halfPixel.x,halfPixel.y),0.035,border);
    if (confidence <= 0.0) return original;
    vec2 sourceUv = clamp(warped,halfPixel,1.0-halfPixel);
    ivec2 base = ivec2(floor(sourceUv*size-0.5));
    // Test the complete bilinear footprint, even if the host uses nearest filtering.
    // Protect nearby sources and do not import an unresolved transparent layer
    // from the completed scene into a different pixel.
    for (int y=0;y<2;y++) for (int x=0;x<2;x++) {
        ivec2 tap = clamp(base+ivec2(x,y),ivec2(0),ivec2(size)-1);
        float depth = texelFetch(OpaqueDepth,tap,0).r;
        vec2 tapUv = (vec2(tap)+0.5)/size;
        float actual = distanceAt(tapUv,depth);
        float tolerance = max(0.002,min(Radius*0.03,minimumDistance*0.01));
        confidence *= 1.0-smoothstep(tolerance,tolerance*2.0,minimumDistance-actual);
        float sourceSurface=distanceAt(tapUv,texelFetch(SurfaceDepth,tap,0).r);
        if(completeScene && sourceSurface<actual-max(0.002,actual*0.00001)) confidence=0.0;
    }
    accepted=confidence;
    return mix(original,completeScene?texture(Scene,sourceUv).rgb:texture(Opaque,sourceUv).rgb,confidence);
}
vec3 backgroundAt(vec2 uv,vec2 warped,float minimumDistance,float confidence,bool completeScene,vec3 original) {
    float accepted;return backgroundSample(uv,warped,minimumDistance,confidence,completeScene,original,accepted);
}
void main() {
    vec4 host = texture(Scene,texCoord);
    vec3 direction = rayAt(texCoord);
    float opaque = distanceAt(texCoord,texture(OpaqueDepth,texCoord).r);
    float surface = distanceAt(texCoord,texture(SurfaceDepth,texCoord).r);
    bool transparent=surface<opaque-max(0.002,opaque*0.00001);
    if(!transparent) surface=opaque;
    vec3 distantGlare=singularityGlare(texCoord,opaque,surface,transparent);
    vec2 bounds = sphere(Eye-Center,direction,max(bodyExtent(),FinaleAmount>0.0?max(max(32.0,Radius*1.5),WaveSupport):0.0));
    if (bounds.y <= 0.0 || max(0.0,bounds.x) >= opaque) {
        vec3 halo=bloomAt(texCoord,opaque)+distantGlare;
        if(DisplaySpace==1) halo=halo/(vec3(1.0)+halo);
        Color=LayerMode==2?vec4(halo,1.0):vec4(host.rgb+halo,host.a); return;
    }
    vec2 core = sphere(Eye-Center,direction,max(coreRadius(),0.000001));
    float coreHit = core.y > 0.0 ? max(0.0,core.x) : 1000000.0;
    bool captured = coreHit < opaque;
    Field field;
    field.front=vec4(0.0); field.back=vec4(0.0); field.lens=vec4(0.0); field.meta=vec4(0.0);
    vec2 size=vec2(textureSize(FrontTexture,0)), coordinate=texCoord*size-0.5;
    ivec2 base=ivec2(floor(coordinate)); vec2 fraction=fract(coordinate);
    float total=0.0, lensTotal=0.0;
    vec3 lensSource=vec3(0.0);
    vec3 dominantSource=vec3(texCoord,-1.0);
    vec2 sourceMin=vec2(1e20), sourceMax=vec2(-1e20);
    float dominantWeight=0.0;
    for(int offsetY=0;offsetY<2;offsetY++) for(int offsetX=0;offsetX<2;offsetX++) {
        ivec2 tap=clamp(base+ivec2(offsetX,offsetY),ivec2(0),ivec2(size)-1);
        vec4 meta=texelFetch(MetaTexture,tap,0);
        float weight=(offsetX==0?1.0-fraction.x:fraction.x)*(offsetY==0?1.0-fraction.y:fraction.y);
        float tolerance=max(Radius*0.08,min(opaque,1000.0)*0.001);
        weight*=exp(-abs(meta.x-opaque)/tolerance-abs(meta.y-surface)/tolerance);
        if ((meta.z>0.5)!=captured) weight=0.0;
        field.front+=texelFetch(FrontTexture,tap,0)*weight; field.back+=texelFetch(BackTexture,tap,0)*weight;
        vec4 lens=texelFetch(LensTexture,tap,0);
        field.lens.zw+=lens.zw*weight; total+=weight;
        // An invalid projection must not drag valid lens coordinates toward the
        // identity mapping during the half-resolution reconstruction either.
        if(meta.w>=0.0 && weight>0.00001 && !any(isnan(lens.xy)) && !any(isinf(lens.xy))) {
            vec3 source=vec3(lens.xy,meta.w);
            lensSource+=source*weight; lensTotal+=weight;
            sourceMin=min(sourceMin,lens.xy); sourceMax=max(sourceMax,lens.xy);
            if(weight>dominantWeight) { dominantWeight=weight; dominantSource=source; }
        }
    }
    float lensConfidence;
    if(total<0.01) { field=integrate(texCoord); lensConfidence=field.meta.w>=0.0?1.0:0.0; }
    else {
        field.front/=total; field.back/=total; field.lens.zw/=total;
        lensConfidence=lensTotal/total;
        field.lens.xy=lensTotal>0.0001?lensSource.xy/lensTotal:texCoord;
        field.meta.w=lensTotal>0.0001?lensSource.z/lensTotal:-1.0;
        vec2 discontinuityLimit=clamp(4.0/size,vec2(0.004),vec2(0.025));
        if(lensTotal>0.0001 && any(greaterThan(sourceMax-sourceMin,discontinuityLimit))) {
            field.lens.xy=dominantSource.xy;
            field.meta.w=dominantSource.z;
        }
    }
    vec3 transmission=transparent?clamp(texture(Transmission,texCoord).rgb,0.0,1.0):vec3(1.0);
    if(captured) { if(coreHit<surface) field.front.a=0.0; else field.back.a=0.0; }
    vec3 original=transparent?texture(Opaque,texCoord).rgb:host.rgb;
    vec3 background=backgroundAt(texCoord,field.lens.xy,field.meta.w,lensConfidence,!transparent,original);
    // Shockwave rejection returns to the existing lens, never the unwarped scene.
    vec3 wave=energyRefraction(texCoord,field.lens.xy,field.meta.w,direction,opaque);
    if(wave.z>=0.) background=backgroundAt(texCoord,wave.xy,wave.z,field.meta.w>=0.?lensConfidence:1.,!transparent,background);
    vec4 spectrum=energySpectrum(direction,opaque);
    if(spectrum.w>=0.) {
        vec2 baseUv=wave.z>=0.?wave.xy:field.meta.w>=0.?field.lens.xy:texCoord;
        float guard=max(max(field.meta.w,wave.z),spectrum.w),redValid,blueValid;
        float confidence=field.meta.w>=0.?lensConfidence:1.;
        vec3 red=backgroundSample(texCoord,baseUv+spectrum.xy,guard,confidence,!transparent,background,redValid);
        vec3 blue=backgroundSample(texCoord,baseUv-spectrum.xy,guard,confidence,!transparent,background,blueValid);
        vec3 split=vec3(red.r,background.g,blue.b);
        vec3 normal=normalize(Eye-Center+direction*spectrum.w);
        float phase=dot(normal,vec3(3.1,5.7,-4.3))+WaveRadius*.13;
        vec3 tint=.35+.65*(.5+.5*cos(phase+vec3(0,2.0944,4.1888)));
        vec3 colored=split*tint;
        colored*=dot(split,vec3(.2126,.7152,.0722))/max(.00001,dot(colored,vec3(.2126,.7152,.0722)));
        // Only the existing scenery is split/tinted; wave light and the singularity
        // are composed below. Reject unresolved RGB sources back to the valid lens.
        background=mix(background,mix(split,colored,spectrum.z*.95),min(redValid,blueValid));
    }
    // An opaque/sky pixel has no separate surface residual: use the completed
    // scene as its background, including clouds drawn after the early snapshot.
    vec3 surfaceRadiance=transparent?host.rgb-transmission*texture(Opaque,texCoord).rgb:vec3(0.0);
    vec3 radiance=field.front.rgb+field.front.a*transmission*field.back.rgb;
    vec3 baseColor=field.front.a*(surfaceRadiance+transmission*field.back.a*background);
    float impact=length(cross(Eye-Center,direction));
    float rim=exp(-abs(impact-coreRadius())/max(fwidth(impact)*1.3,Radius*0.003));
    vec3 rimLight=FinaleAmount<0.99999 && core.y>0.0 && coreHit<opaque?innerTransitionColor(HotColor,Radius)*rim*Brightness*0.18*(1.+(IntroEnabled!=0?IntroTighten*4.:0.)):vec3(0.0);
    radiance+=rimLight*(coreHit<surface?vec3(1.0):transmission);
    vec3 bloom=bloomAt(texCoord,opaque)+distantGlare;
    if(LayerMode==1) { Color=vec4(max(vec3(0.0),surfaceRadiance+transmission*(field.back.rgb+field.back.a*background+(coreHit>=surface?rimLight:vec3(0.0)))+bloom),host.a); return; }
    if(LayerMode==2) { Color=vec4(field.front.rgb+(coreHit<surface?rimLight:vec3(0.0))+bloom,field.front.a); return; }
    vec3 light=radiance+bloom;
    // Vanilla receives display-space light; shader packs retain their own HDR tonemapping.
    if(DisplaySpace==1) light=light/(vec3(1.0)+light);
    Color=vec4(max(vec3(0.0),baseColor+light),host.a);
}
