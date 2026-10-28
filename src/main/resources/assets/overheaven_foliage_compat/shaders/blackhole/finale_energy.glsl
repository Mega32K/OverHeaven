// Finite analytic radiance with the existing opaque/transparent split; late refraction is separate.
uniform float FinaleOrbit;
float energyHash(float value) { return fract(sin(value*73.17+Seed)*43758.5453); }
vec3 energyRotate(vec3 point,vec3 axis,float angle) {
    return point*cos(angle)+cross(axis,point)*sin(angle)+axis*dot(axis,point)*(1.-cos(angle));
}
void energySegment(inout Field f,vec3 origin,vec3 ray,vec3 a,vec3 b,float light,float opaque,float surface,vec3 tint) {
    vec3 segment=b-a; float distance=length(segment); vec3 axis=segment/max(distance,.0001);
    float cosine=dot(ray,axis),denominator=max(.0001,1.-cosine*cosine);
    float along=clamp((dot(origin-a,axis)-cosine*dot(origin-a,ray))/denominator,0.,distance);
    vec3 point=a+axis*along; float hit=max(0.,dot(point-origin,ray));
    float width=max(Radius*.006,max(hit,1.)*PixelAngle*.65);
    vec3 gap=origin+ray*hit-point;
    float coverage=exp(-dot(gap,gap)/(width*width));
    float strand=.28+.72*pow(.5+.5*sin(length(point)*1.7-FinaleOrbit*4.),5.);
    finaleLight(f,tint*coverage*light*strand,hit,opaque,surface);
}
// CPU model is also used by the camera crossing detector. No phase-local expiry here.
uniform int WavePresent,WaveFinal;
uniform float WaveRadius,WaveImpulse,WaveBreakup,WaveExtent,WaveAge,WaveAmplitude,WaveSupport;
vec2 energyEvent() { return WavePresent!=0?vec2(WaveAge,WaveAmplitude):vec2(-1.,0.); }
vec4 energyWave(float age) { return vec4(WaveRadius,WaveImpulse,WaveBreakup,WaveExtent); }
float energyPatch(vec3 local,float amplitude) {
    // World-space angular patches, no atan seam or per-frame random noise.
    return .5+.27*sin(dot(local,vec3(11.,17.,7.))+Seed+amplitude*3.)
        +.23*sin(dot(local,vec3(-23.,9.,19.))-Seed*.7);
}
float energyCoherence(vec3 local,vec4 wave,float amplitude) {
    return smoothstep(wave.z*1.14-.13,wave.z*1.14+.13,energyPatch(local,amplitude));
}
void energyFront(inout Field f,vec3 origin,vec3 ray,float opaque,float surface) {
    vec2 event=energyEvent(); if(event.x<0.) return;
    float age=event.x,amplitude=event.y; vec4 wave=energyWave(age);
    for(int layer=0;layer<3;layer++) {
        float radius=wave.x;
        if(layer==1) radius-=wave.w*.024*smoothstep(.09,.22,age);
        if(layer==2) {
            if(age<=.48) continue;
            radius+=wave.w*.12*smoothstep(.48,1.,age);
        }
        vec2 hits=sphere(origin,ray,max(radius,.001)); if(hits.y<=0.) continue;
        for(int side=0;side<2;side++) {
            float hit=side==0?hits.x:hits.y; if(hit<0. || hit>=opaque) continue;
            vec3 normal=normalize(origin+ray*hit);
            vec3 local=vec3(dot(normal,DiskRight),dot(normal,DiskNormal),dot(normal,DiskForward));
            float incidence=clamp(abs(dot(normal,ray)),0.,1.);
            float edge=pow(1.-incidence,layer==0?8.:layer==1?4.:5.);
            float patch=energyPatch(local,amplitude),coherence=energyCoherence(local,wave,amplitude);
            float lace=.6+.4*pow(.5+.5*sin(dot(local,vec3(39.,-21.,33.))),3.);
            float strength=(.003+edge)*coherence*lace;
            if(layer==1) strength*=.32*smoothstep(.09,.18,age);
            if(layer==2) strength=(.001+edge)*smoothstep(.63,.88,patch)
                *smoothstep(.48,.66,age)*.85;
            float footprint=max(hit,1.)*PixelAngle;
            float silhouette=smoothstep(0.,sqrt(max(.00001,radius*footprint))*2.,hits.y-hits.x);
            vec3 tint=mix(vec3(.43,.74,1.),vec3(1.,.90,.70),smoothstep(.25,.85,patch));
            finaleLight(f,tint*strength*silhouette*wave.y*amplitude*Brightness,hit,opaque,surface);
        }
    }
}
// Only the late composite calls this. The field's gravitational UV/guard stay intact.
// Rotate the already bent direction, then let the full-resolution source-depth check
// fall back to the gravitational image if this additional sample cannot be resolved.
vec3 energyRefraction(vec2 uv,vec2 baseUv,float baseGuard,vec3 ray,float opaque) {
    vec2 event=energyEvent(); if(event.x<0. || FinaleAmount<=.00001) return vec3(uv,-1.);
    vec4 wave=energyWave(event.x); vec3 origin=Eye-Center,bend=vec3(0.);
    float guard=max(baseGuard,0.);
    for(int layer=0;layer<2;layer++) {
        float radius=wave.x-(layer==1?wave.w*.024*smoothstep(.09,.22,event.x):0.);
        vec2 hits=sphere(origin,ray,max(radius,.001));
        float hit=hits.x>=0.?hits.x:hits.y;
        if(hit<0. || hit>=opaque) continue;
        vec3 normal=normalize(origin+ray*hit);
        vec3 local=vec3(dot(normal,DiskRight),dot(normal,DiskNormal),dot(normal,DiskForward));
        float edge=pow(max(0.,1.-abs(dot(normal,ray))),6.);
        float strength=edge*energyCoherence(local,wave,event.y)*wave.y*event.y*FinaleAmount;
        strength*=layer==0?.016:-.005*smoothstep(.09,.18,event.x);
        if(abs(strength)<.000001) continue;
        bend+=normal*strength; guard=max(guard,hit);
    }
    if(dot(bend,bend)<1.e-12) return vec3(uv,-1.);
    vec3 base=baseGuard>=0.?rayAt(baseUv):ray;
    vec3 outgoing=normalize(base+bend-base*dot(base,bend));
    vec4 projected=VP*vec4(outgoing,0.);
    if(projected.w<=.00001 || any(isnan(projected)) || any(isinf(projected))) return vec3(uv,-1.);
    return vec3(projected.xy/projected.w*.5+.5,guard);
}
// Every pressure halo refracts SCENERY on both sides of its luminous wave rim.
// It is independent of wave radiance/coherence: dark gaps still disperse background.
vec4 energySpectrum(vec3 direction,float opaque) {
    if(WavePresent==0 || FinaleAmount<=0.) return vec4(0,0,0,-1);
    vec3 origin=Eye-Center;
    float width=max(Radius*.90,WaveRadius*.36);
    vec2 shell=sphere(origin,direction,WaveRadius+width);
    if(shell.y<=0.) return vec4(0,0,0,-1);
    float guard=max(0.,shell.x);
    if(guard>=opaque) return vec4(0,0,0,-1);
    float closest=max(0.,-dot(origin,direction));
    float impact=length(origin+direction*closest);
    float band=1.-smoothstep(width*.12,width,abs(impact-WaveRadius));
    float strength=clamp(band*WaveImpulse*FinaleAmount,0.,1.)*(WaveFinal!=0?1.:.95);
    if(strength<.00001) return vec4(0,0,0,-1);
    vec3 normal=normalize(origin+direction*closest);
    float ripple=.88+.12*sin(dot(normal,vec3(7.1,-9.3,5.7))+WaveRadius*.21+Seed);
    vec3 outgoing=normalize(direction+(normal-direction*dot(normal,direction))*(.080*ripple*strength));
    vec4 base=VP*vec4(direction,0.),shift=VP*vec4(outgoing,0.);
    if(base.w<=.00001 || shift.w<=.00001 || any(isnan(shift)) || any(isinf(shift))) return vec4(0,0,0,-1);
    vec2 uvShift=(shift.xy/shift.w-base.xy/base.w)*.5;
    // Up to 80 pixels per channel at 1080p, with a pixel-isotropic cap at every FOV.
    vec2 pixels=uvShift*vec2(textureSize(OpaqueDepth,0));
    float limit=80.*float(textureSize(OpaqueDepth,0).y)/1080.;
    uvShift*=min(1.,limit/max(length(pixels),.0001));
    return vec4(uvShift,strength,guard);
}
void finaleEnergy(inout Field f,vec3 origin,vec3 ray,float opaque,float surface,float fade) {
    if(FinalePhase==0) {
        float denominator=dot(ray,DiskNormal);
        if(abs(denominator)>.0001) {
            float hit=-dot(origin,DiskNormal)/denominator; vec3 at=origin+ray*hit;
            float angle=atan(dot(at,DiskForward),dot(at,DiskRight));
            float radius=mix(Inner,Outer*1.22,smoothstep(0.,.82,FinaleProgress));
            float width=max(Radius*.012,max(hit,1.)*PixelAngle);
            float fragments=pow(.5+.5*sin(angle*11.+sin(angle*3.)*2.-FinaleTime*.7),5.);
            float rim=exp(-pow((length(at)-radius)/width,2.))*fragments*(1.-smoothstep(.08,.82,FinaleProgress));
            finaleLight(f,vec3(1.,.84,.57)*rim*Brightness*.65,hit,opaque,surface);
        }
    }
    if(FinalePhase>=1 && FinalePhase<=4) {
        float summon=FinalePhase==1?smoothstep(.24,.85,FinaleProgress):1.;
        float dim=FinalePhase==3?mix(1.,.28,smoothstep(.15,1.,FinaleProgress)):FinalePhase==4?.28*(1.-smoothstep(.12,.92,FinaleProgress)):1.;
        int count=Steps<=24?6:Steps<=48?8:10;
        float extent=max(Radius*2.,min(30.,Outer*1.24));
        for(int lane=0;lane<10;lane++) {
            if(lane>=count) break;
            float h=energyHash(float(lane)); float angle=float(lane)*2.399963+Seed*.1;
            vec3 axis=normalize(vec3(cos(angle)*.85,h*1.8-.9,sin(angle)*.85));
            vec3 tangent=normalize(cross(axis,vec3(.21,.96,.14)));
            vec3 previous=axis*Radius*.92;
            float reversal=FinalePhase==3?smoothstep(.12,.94,FinaleProgress):FinalePhase>=4?1.:0.;
            for(int segment=1;segment<=5;segment++) {
                float fraction=float(segment)/5.,radius=mix(Radius*.92,extent,fraction);
                vec3 tip=axis*radius+tangent*fraction*fraction*sin(FinaleOrbit+h*6.28)*extent*.18;
                tip=energyRotate(tip,DiskNormal,reversal*fraction*fraction*2.7);
                vec3 tint=mix(vec3(.3,.66,1.),vec3(1.,.77,.42),h);
                energySegment(f,origin,ray,previous,tip,summon*dim*Brightness*.38,opaque,surface,tint);
                previous=tip;
            }
        }
    }
    energyFront(f,origin,ray,opaque,surface);
}
