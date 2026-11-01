#version 330 core
in vec2 texCoord;
layout(location=0) out vec4 Color;
layout(location=1) out vec4 CachedFront;
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
#include "composite_light.glsl"
#include "reconstruct.glsl"
vec3 backgroundSample(vec2 uv, vec2 warped, float minimumDistance, float confidence, bool completeScene, vec3 original,out float accepted) {
    accepted=0.;
    if (minimumDistance < 0.0 || confidence <= 0.0) return original;
    vec2 size = vec2(textureSize(Scene,0));
    vec2 halfPixel = 0.5 / size;
    float border = min(min(warped.x,warped.y),min(1.0-warped.x,1.0-warped.y));
    confidence *= smoothstep(max(halfPixel.x,halfPixel.y),max(halfPixel.x,halfPixel.y)*12.0,border);
    if (confidence <= 0.0) return original;
    vec2 sourceUv = clamp(warped,halfPixel,1.0-halfPixel);
    ivec2 base = ivec2(floor(sourceUv*size-0.5));
    // Test the complete bilinear footprint, even if the host uses nearest filtering.
    // Protect nearby sources and do not import an unresolved transparent layer
    // from the completed scene into a different pixel.
    for (int y=0;y<2;y++) for (int x=0;x<2;x++) {
        ivec2 tap = clamp(base+ivec2(x,y),ivec2(0),ivec2(size)-1);
        vec2 tapUv = (vec2(tap)+0.5)/size;
        float depth = texture(OpaqueDepth,tapUv).r;
        float actual = distanceAt(tapUv,depth);
        float tolerance = max(0.002,min(Radius*0.03,minimumDistance*0.01));
        confidence *= 1.0-smoothstep(tolerance,tolerance*2.0,minimumDistance-actual);
        float sourceSurface=distanceAt(tapUv,texture(SurfaceDepth,tapUv).r);
        if(completeScene && sourceSurface<actual-max(0.002,actual*0.00001)) confidence=0.0;
    }
    accepted=confidence;
    return mix(original,completeScene?texture(Scene,sourceUv).rgb:texture(Opaque,sourceUv).rgb,confidence);
}
vec3 backgroundAt(vec2 uv,vec2 warped,float minimumDistance,float confidence,bool completeScene,vec3 original) {
    float accepted;return backgroundSample(uv,warped,minimumDistance,confidence,completeScene,original,accepted);
}
void main() {
    CachedFront=vec4(0,0,0,1);
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
        if(DisplaySpace==1) halo=displayLight(halo);
        Color=LayerMode==2?vec4(finiteHighRadiance(halo),1.0):vec4(finiteHighRadiance(host.rgb+halo),host.a); return;
    }
    vec2 core = sphere(Eye-Center,direction,max(coreRadius(),0.000001));
    float coreHit = core.y > 0.0 ? max(0.0,core.x) : 1000000.0;
    bool captured = !relativistic() && coreHit < opaque;
    Field field; float total;
    if(relativistic()) {
        // High subjects, including the show's black hole, use native resolution.
        // attachments from exactly one ray, including its own horizon event.
        ivec2 tap=ivec2(gl_FragCoord.xy);
        if(all(equal(textureSize(FrontTexture,0),ivec2(SceneSize))) && solutionContains(tap)) {
            field.front=texelFetch(FrontTexture,tap,0);field.back=texelFetch(BackTexture,tap,0);
            field.lens=texelFetch(LensTexture,tap,0);field.meta=texelFetch(MetaTexture,tap,0);
        } else field=integrate(texCoord);
        captured=field.meta.z>.5;
    } else {
        reconstructRadiance(texCoord,opaque,surface,captured,field,total);
        if(total<0.01) field=integrate(texCoord);
    }
    // Full-resolution analytic map: never average distinct projected sources.
    vec3 mapping=vec3(texCoord,-1.0);
    if(relativistic() && FinaleAmount<.99999) mapping=vec3(field.lens.xy,field.meta.w);
    else {
        if(!captured) mapping=environmentLens(texCoord,direction,opaque,sphere(Eye-Center,direction,Outer*1.35+Thickness*2.));
        field.lens.xy=mapping.xy; field.meta.w=mapping.z;
    }
    float lensConfidence=mapping.z>=0.0?1.0:0.0;
    // Finite world-overlay support is a presentation boundary, not a force cutoff.
    // The traced ray remains unchanged; only unsupported outer background fades.
    if(relativistic() && FinaleAmount<.99999) lensConfidence*=1.-smoothstep(bodyExtent()*.75,bodyExtent(),length(cross(Eye-Center,direction)));
    vec3 transmission=transparent?clamp(texture(Transmission,texCoord).rgb,0.0,1.0):vec3(1.0);
    if(captured && !relativistic()) { if(coreHit<surface) field.front.a=0.0; else field.back.a=0.0; }
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
    vec3 rimLight=core.y>0.0 && coreHit<opaque?introHorizonRim(impact,fwidth(impact)):vec3(0.0);
    CachedFront=vec4(field.front.rgb+(coreHit<surface?rimLight:vec3(0.0)),field.front.a);
    radiance+=rimLight*(coreHit<surface?vec3(1.0):transmission);
    vec3 bloom=bloomAt(texCoord,opaque)+distantGlare;
    if(LayerMode==1) { Color=vec4(finiteHighRadiance(max(vec3(0.0),surfaceRadiance+transmission*(field.back.rgb+field.back.a*background+(coreHit>=surface?rimLight:vec3(0.0)))+bloom)),host.a); return; }
    if(LayerMode==2) { Color=vec4(finiteHighRadiance(field.front.rgb+(coreHit<surface?rimLight:vec3(0.0))+bloom),field.front.a); return; }
    vec3 light=radiance+bloom;
    // Vanilla receives display-space light; shader packs retain their own HDR tonemapping.
    if(DisplaySpace==1) light=displayLight(light);
    Color=vec4(finiteHighRadiance(max(vec3(0.0),baseColor+light)),host.a);
}
