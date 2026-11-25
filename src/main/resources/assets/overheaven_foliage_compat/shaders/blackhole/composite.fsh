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
// On the projected line from the lens to infinity, reciprocal clip w is
// (1-t)/pivotW. An opaque source must project back onto its own depth.
float lensDepthT(vec2 uv,float depth,float pivotW) {
    if(depth>=.9999999) return 1.;
    float inverseW=(InverseVP*vec4(uv*2.-1.,depth*2.-1.,1.)).w;
    return 1.-pivotW*inverseW;
}
float lensSurfaceT(vec2 uv,float pivotW) { return lensDepthT(uv,textureLod(OpaqueDepth,uv,0.).r,pivotW); }
float depthSlope(float left,float center,float right) {
    float a=center-left,b=right-center;
    if(max(left,right)>=.9999999 || abs(a-b)>2e-7+.15*max(abs(a),abs(b))) return 0.;
    return .5*(a+b);
}
vec2 lensDepthPlane(vec2 uv,float pivotW,vec2 size,vec2 delta) {
    float d=textureLod(OpaqueDepth,uv,0.).r;
    if(d>=.9999999) return vec2(1.,0.);
    // Window depth is affine across a rasterized planar face. Reconstruct it
    // inside this texel only on axes where both neighbours confirm the slope.
    // Nearest depth alone makes grazing ground alternate between hits and holes.
    vec2 pixel=uv*size;
    vec2 slope=vec2(0.);
    if(all(greaterThanEqual(pixel,vec2(1.))) && all(lessThan(pixel,size-1.))) {
        slope.x=depthSlope(textureLodOffset(OpaqueDepth,uv,0.,ivec2(-1,0)).r,d,textureLodOffset(OpaqueDepth,uv,0.,ivec2(1,0)).r);
        slope.y=depthSlope(textureLodOffset(OpaqueDepth,uv,0.,ivec2(0,-1)).r,d,textureLodOffset(OpaqueDepth,uv,0.,ivec2(0,1)).r);
        d+=dot(fract(pixel)-.5,slope);
    }
    float change=(InverseVP*vec4(delta,dot(slope,delta*size),0.)).w;
    return vec2(lensDepthT(uv,d,pivotW),-2.*pivotW*change);
}
float lensPlaneHit(vec2 uv,vec2 delta,float pivotW,float plane,float limit,float best) {
    if(plane>=0. && plane<min(1.,best) && plane<=limit && lensSurfaceT(uv+delta*plane,pivotW)==plane) return plane;
    return best;
}
vec3 visibleLensSource(vec2 uv,vec3 direction,vec2 skyUv,float pivot,float foreground,float destinationDepth) {
    vec3 invalid=vec3(uv,-1.);
    if(pivot<0.) return invalid;
    // The resolved color texel must intersect the outgoing ray. Its neighbours
    // may straddle the virtual lens plane on a continuous slope: only protect
    // the foreground ahead of the actual field when checking the filter footprint.
    if(all(equal(textureSize(OpaqueDepth,0),ivec2(1)))) return vec3(skyUv,foreground);

    vec2 delta=skyUv-uv,size=vec2(textureSize(Scene,0)),margin=.5/size;
    vec2 room=mix(uv-margin,1.-margin-uv,greaterThan(delta,vec2(0.)));
    vec2 end=mix(vec2(1.),room/max(abs(delta),vec2(1e-8)),greaterThan(abs(delta),vec2(1e-8)));
    float limit=clamp(min(end.x,end.y),0.,1.);
    float span=max(abs(delta.x)*size.x,abs(delta.y)*size.y);
    if(span<.5 || limit<=0.) return invalid; // Both directions select the same scene texel, or leave the frame.
    float pivotW=(VP*vec4(Eye+direction*pivot,1.)).w;
    float previous=0.,leftSurface=lensDepthT(uv,destinationDepth,pivotW);
    if(pivotW<=0. || leftSurface<0.) return invalid;
    // No other texel can occlude a hit before the ray leaves its starting texel.
    if(!relativistic() && leftSurface<1. && leftSurface*span<.5) return vec3(uv+delta*leftSurface,foreground);
    const int maxSteps=6;
    int count=int(clamp(ceil(span*limit),1.,float(maxSteps)));
    float hit=lensPlaneHit(uv,delta,pivotW,leftSurface,limit,2.);
    bool startsInSky=leftSurface==1.;
    vec2 skyEdge=vec2(-1.);
    vec2 stepPx=delta*size;
    vec2 across=normalize(vec2(-stepPx.y,stepPx.x))/size;
    for(int i=1;i<=maxSteps;i++) {
        if(i>count) break;
        float t=limit*float(i)/float(count);
        float rightSurface=lensSurfaceT(uv+delta*t,pivotW);
        if(startsInSky && skyEdge.x<0. && rightSurface<1.) skyEdge=vec2(previous,t);
        // A step can jump off a thin ridge. Reproject each newly encountered
        // depth onto the ray, even if the intersection was between earlier taps.
        if(rightSurface!=leftSurface) hit=lensPlaneHit(uv,delta,pivotW,rightSurface,limit,hit);
        if(leftSurface==1. && rightSurface==1. && span*limit>float(count)) {
            // Sparse sky taps can straddle a grazing silhouette. Its neighbours
            // only suggest a depth; the color lookup must still lie on the ray.
            vec2 midpoint=uv+delta*((previous+t)*.5);
            if(all(lessThan(abs(midpoint-.5)+abs(across),.5-margin))) {
                hit=lensPlaneHit(uv,delta,pivotW,lensSurfaceT(midpoint-across,pivotW),limit,hit);
                hit=lensPlaneHit(uv,delta,pivotW,lensSurfaceT(midpoint+across,pivotW),limit,hit);
            }
        } else if(!relativistic() && leftSurface==1. && rightSurface==1.) {
            // A subpixel step can cross both a row and a column boundary. The
            // briefly visited third texel may contain the entire ridge hit.
            vec2 cell=floor((uv+delta*previous)*size),next=floor((uv+delta*t)*size);
            if(all(notEqual(cell,next))) {
                vec2 crossings=(max(cell,next)-uv*size)/stepPx;
                vec2 middle=uv+delta*((crossings.x+crossings.y)*.5);
                hit=lensPlaneHit(uv,delta,pivotW,lensSurfaceT(middle,pivotW),limit,hit);
            }
        }
        float previousError=leftSurface-previous;
        if(leftSurface!=rightSurface && rightSurface<=t && rightSurface<1. && previousError>=0. && previous<hit) {
            // Coarse depths are texel-centred. The subpixel crossing can fall
            // just across a step endpoint on an otherwise continuous face.
            float lo=max(0.,previous-1./span),hi=min(limit,t+1./span);
            // Intersect the local face. Accept only if the intersection remains
            // in the texel that supplied it; bisect past discontinuities.
            float probe=mix(previous,t,previousError/max(previousError+t-rightSurface,1e-8));
            for(int j=0;j<8;j++) {
                vec2 q=uv+delta*probe,plane=lensDepthPlane(q,pivotW,size,delta);
                float root=abs(1.-plane.y)>1e-6?probe+(plane.x-probe)/(1.-plane.y):-1.;
                if(plane.x<1. && root>=0. && root<=limit) {
                    vec2 source=uv+delta*root;
                    if(all(equal(floor(q*size),floor(source*size)))) { hit=min(hit,root);break; }
                    if(abs(root-probe)*span<1.) {
                        // Quantized depths on adjacent texels can place the root
                        // on opposite sides of their shared subpixel boundary.
                        vec2 other=lensDepthPlane(source,pivotW,size,delta);
                        if(other.x<1. && abs(other.x-root)*span<.125*abs(1.-other.y)) { hit=min(hit,root);break; }
                    }
                }
                if(plane.x>probe) lo=probe; else hi=probe;
                probe=root>lo && root<hi?root:(lo+hi)*.5;
            }
        }
        previous=t;leftSurface=rightSurface;
        if(t>=hit) break;
    }
    if(hit>1. && limit==1.) {
        if(leftSurface==1.) hit=1.;
        else if(skyEdge.x>=0.) {
            // Hidden sky: shorten the sky ray within its first visible interval.
            // Leave room before the silhouette so sky pixels do not pin to it.
            for(int i=0;i<5;i++) {
                float mid=(skyEdge.x+skyEdge.y)*.5;
                if(lensSurfaceT(uv+delta*mid,pivotW)==1.) skyEdge.x=mid; else skyEdge.y=mid;
            }
            float visible=skyEdge.x*(.5+.5*skyEdge.x);
            if(lensSurfaceT(uv+delta*visible,pivotW)==1.) hit=visible;
        }
    }
    if(hit>1.) return invalid;
    return vec3(uv+delta*hit,foreground);
}
vec3 backgroundMapping(vec2 uv,vec3 direction,float opaque,float destinationDepth,bool captured,vec2 skyUv,float pivot,float fieldEntry) {
    float foreground;
    if(relativistic() && FinaleAmount<.99999) {
        foreground=max(fieldEntry,highMass()*kerrHorizon(Spin));
    } else {
        if(captured) return vec3(uv,-1.);
        vec2 bounds=sphere(Eye-Center,direction,Outer*1.35+Thickness*2.);
        if(FinaleAmount>=.99999) return environmentLens(uv,direction,opaque,bounds);
        pivot=max(dot(Center-Eye,direction),Radius);
        if(opaque<=pivot) return vec3(uv,-1.);
        // The complete chord gives the deflection at infinity. Shortening the
        // chord to the destination's depth here would apply parallax twice.
        vec3 sky=environmentLens(uv,direction,bounds.y,bounds);
        if(sky.z<0.) return vec3(uv,-1.);
        skyUv=sky.xy;
        foreground=max(bounds.x,Radius);
    }
    return visibleLensSource(uv,direction,skyUv,pivot,foreground,destinationDepth);
}
vec3 backgroundSample(vec2 warped,float minimumDistance,float confidence,bool completeScene,bool opaqueBacking,vec3 original,out float accepted) {
    accepted=0.;
    if (minimumDistance < 0.0 || confidence <= 0.0) return original;
    vec2 size = vec2(textureSize(Scene,0));
    vec2 halfPixel = 0.5 / size;
    float border = min(min(warped.x,warped.y),min(1.0-warped.x,1.0-warped.y));
    confidence *= smoothstep(max(halfPixel.x,halfPixel.y),max(halfPixel.x,halfPixel.y)*12.0,border);
    if (confidence <= 0.0) return original;
    vec2 sourceUv = clamp(warped,halfPixel,1.0-halfPixel);
    ivec2 base = ivec2(floor(sourceUv*size-0.5));
    ivec2 sourcePixel=ivec2(sourceUv*size);
    bool useScene=completeScene || opaqueBacking;
    float tolerance = max(0.002,min(Radius*0.03,minimumDistance*0.01));
    // Test the complete bilinear footprint, even if the host uses nearest filtering.
    // The scene and opaque captures use nearest filtering. For the ordinary lens,
    // a water/glass source has a known opaque color underneath its host surface.
    for (int y=0;y<2;y++) for (int x=0;x<2;x++) {
        ivec2 tap = clamp(base+ivec2(x,y),ivec2(0),ivec2(size)-1);
        vec2 tapUv = (vec2(tap)+0.5)/size;
        float depth = texture(OpaqueDepth,tapUv).r;
        float actual = distanceAt(tapUv,depth);
        confidence *= 1.0-smoothstep(tolerance,tolerance*2.0,minimumDistance-actual);
        if(opaqueBacking?all(equal(tap,sourcePixel)):completeScene) {
            float surfaceDepth=texture(SurfaceDepth,tapUv).r;
            if(surfaceDepth<depth && distanceAt(tapUv,surfaceDepth)<actual-max(0.002,actual*0.00001)) {
                if(opaqueBacking) useScene=false; else confidence=0.;
            }
        }
    }
    accepted=confidence;
    return mix(original,useScene?texture(Scene,sourceUv).rgb:texture(Opaque,sourceUv).rgb,confidence);
}
vec3 backgroundAt(vec2 uv,vec2 warped,float minimumDistance,float confidence,bool completeScene,vec3 original) {
    if(FinaleAmount<.99999 && all(lessThan(abs(warped-uv)*vec2(textureSize(Scene,0)),vec2(.5)))) return original;
    float accepted;return backgroundSample(warped,minimumDistance,confidence,completeScene,FinaleAmount<.99999,original,accepted);
}
void main() {
    CachedFront=vec4(0,0,0,1);
    vec4 host = texture(Scene,texCoord);
    vec3 direction = rayAt(texCoord);
    float destinationDepth=texture(OpaqueDepth,texCoord).r;
    float opaque = distanceAt(texCoord,destinationDepth);
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
    // Resolve the source at the same full-resolution pixel that samples it.
    vec3 mapping=backgroundMapping(texCoord,direction,opaque,destinationDepth,captured,field.lens.xy,field.meta.w,bounds.x);
    field.lens.xy=mapping.xy; field.meta.w=mapping.z;
    float lensConfidence=mapping.z>=0.0?1.0:0.0;
    vec3 transmission=transparent?clamp(texture(Transmission,texCoord).rgb,0.0,1.0):vec3(1.0);
    if(captured && !relativistic()) { if(coreHit<surface) field.front.a=0.0; else field.back.a=0.0; }
    vec3 original=transparent?texture(Opaque,texCoord).rgb:host.rgb;
    vec3 background=backgroundAt(texCoord,field.lens.xy,field.meta.w,lensConfidence,!transparent,original);
    // Shockwave rejection returns to the existing lens, never the unwarped scene.
    vec3 wave=energyRefraction(texCoord,field.lens.xy,field.meta.w,direction,opaque);
    bool waveBacking=relativistic() && FinaleAmount<.99999;
    if(wave.z>=0.) {
        float accepted;
        background=backgroundSample(wave.xy,wave.z,field.meta.w>=0.?lensConfidence:1.,!transparent,waveBacking,background,accepted);
    }
    vec4 spectrum=energySpectrum(direction,opaque);
    if(spectrum.w>=0.) {
        vec2 baseUv=wave.z>=0.?wave.xy:field.meta.w>=0.?field.lens.xy:texCoord;
        float guard=max(max(field.meta.w,wave.z),spectrum.w),redValid,blueValid;
        float confidence=field.meta.w>=0.?lensConfidence:1.;
        vec3 red=backgroundSample(baseUv+spectrum.xy,guard,confidence,!transparent,waveBacking,background,redValid);
        vec3 blue=backgroundSample(baseUv-spectrum.xy,guard,confidence,!transparent,waveBacking,background,blueValid);
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
