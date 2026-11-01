// Schwarzschild null geodesics in their orbital plane, u=M/r:
// u'' + u = 3u^2, (u')^2 + u^2 - 2u^3 = (M/b)^2.
// See Belbruno & Pretorius (2011), arXiv:1103.0585. No borrowed renderer code.
// A static observer's local direction initializes b=r*sin(psi)/sqrt(1-2M/r).
// All disk samples, horizon absorption and the escaped sky direction use this ray.
#include "thermal.glsl"

vec2 geodesicDerivative(vec2 state) { return vec2(state.y,state.x*(3.*state.x-1.)); }
vec2 geodesicStep(vec2 state,float h) {
    vec2 a=geodesicDerivative(state),b=geodesicDerivative(state+a*(h*.5));
    vec2 c=geodesicDerivative(state+b*(h*.5)),d=geodesicDerivative(state+c*h);
    return state+(a+2.*b+2.*c+d)*(h/6.);
}
float stableDiskInner(float mass) { return max(Inner,6.*mass); }
float thermalFlux(float radius,float inner) {
    float q=inner/max(radius,inner),peakQ=inner/min(Outer,inner*(49./36.));
    return q*q*q*(1.-sqrt(q))/max(peakQ*peakQ*peakQ*(1.-sqrt(peakQ)),1e-9);
}
float orbitalBeta(float mass,float radius) { return sqrt(max(0.,mass/max(radius-2.*mass,1e-8))); }
float frequencyShift(float mass,float radius,float observerRadius,float mu) {
    float f=max(1e-6,1.-2.*mass/radius),fo=max(1e-6,1.-2.*mass/observerRadius);
    float beta=orbitalBeta(mass,radius);
    return sqrt(f/fo)*sqrt(max(0.,1.-beta*beta))/max(1e-6,1.-beta*mu);
}

// Host geometry remains a screen-depth approximation, not a curved world mesh.
// Split each local chord at host distance boundaries; never use optical path
// length as camera depth (a looping ray may accumulate a very long path).
void relativisticSegment(inout Field f,vec3 a,vec3 b,float mass,float observerRadius) {
    float inner=stableDiskInner(mass);
    if(Outer<=inner) return;
    // The terminal escape endpoint is effectively at infinity. Clip its chord
    // before quadrature so a nearby observer looking outward still sees the disk.
    vec3 fullChord=b-a;float fullLength=length(fullChord);
    if(fullLength<1e-9) return;
    vec3 fullDirection=fullChord/fullLength;
    vec2 support=sphere(a,fullDirection,Outer+Thickness*2.);
    float start=max(0.,support.x),finish=min(fullLength,support.y);
    if(finish<=start) return;
    b=a+fullDirection*finish;a+=fullDirection*start;
    float height0=dot(a,DiskNormal),delta=dot(b-a,DiskNormal);
    float begin=0.,end=1.;
    if(abs(delta)<1e-9) { if(abs(height0)>Thickness*2.) return; }
    else {
        vec2 slab=(vec2(-Thickness*2.,Thickness*2.)-height0)/delta;
        begin=max(0.,min(slab.x,slab.y));end=min(1.,max(slab.x,slab.y));
    }
    if(end<=begin) return;
    vec3 chord=b-a;float chordLength=length(chord);
    if(chordLength<1e-9) return;
    vec3 coordinateRay=chord/chordLength,eye=Eye-Center;
    for(int tap=0;tap<2;tap++) {
        float fraction=mix(begin,end,(float(tap)+.5)*.5);
        vec3 gas=mix(a,b,fraction);
        float depth=length(gas-eye);
        if(depth>=f.meta.x) continue;
        vec2 disk=vec2(dot(gas,DiskRight),dot(gas,DiskForward));
        float radius=length(disk),sphericalRadius=length(gas);
        if(radius<=inner || radius>=Outer) continue;
        vec3 radial=gas/max(sphericalRadius,1e-8);
        float metric=max(1e-6,1.-2.*mass/sphericalRadius);
        float radialPart=dot(coordinateRay,radial);
        vec3 properRay=coordinateRay+radial*radialPart*(inversesqrt(metric)-1.);
        float properFactor=length(properRay);
        properRay/=properFactor;
        vec3 tangent=(DiskForward*disk.x-DiskRight*disk.y)/radius;
        // Rotation magnitude is a playback clock; freezing does not stop orbiting gas.
        tangent*=Rotation<0.?-1.:1.;
        float mu=dot(tangent,-properRay);
        float g=frequencyShift(mass,sphericalRadius,observerRadius,mu);
        float segmentLength=chordLength*(end-begin)*.5;
        float footprint=max(depth*PixelAngle,segmentLength*.20);
        vec3 material=diskMaterial(disk,footprint);
        float height=dot(gas,DiskNormal)/max(Thickness,.001);
        float mask=smoothstep(inner,inner+(Outer-inner)*.04,radius)
            *(1.-smoothstep(max(inner+(Outer-inner)*.5,Outer*.74),Outer,radius));
        // Grey comoving opacity. E_em/E_static accounts for the moving gas path.
        float beta=orbitalBeta(mass,sphericalRadius);
        float restLength=segmentLength*properFactor*(1.-beta*mu)/sqrt(max(1e-6,1.-beta*beta));
        float optical=mask*material.x*exp(-height*height*(.55+material.z*.35))*restLength/max(Thickness,.02)*1.4;
        float absorbed=1.-exp(-optical);
        float flux=thermalFlux(radius,inner)*max(material.y*.5,0.);
        // Fixed reference accretion normalization. For a fixed Eddington ratio,
        // the reference temperature scales as M^-1/4, with 12000 K at default M.
        float peakTemperature=12000.*pow(.615840287/max(mass,.0001),.25);
        float temperature=peakTemperature*pow(max(flux,0.),.25);
        // Planck(g*T) already includes the spectral frequency/amplitude transform;
        // do NOT multiply a second D^3 or g^4 into these RGB intensities.
        vec3 grade=mix(ColdColor,HotColor,clamp(flux,0.,1.));
        vec3 emission=thermalRadiance(g*temperature)*grade*Brightness*3.46;
        emission=min(emission,vec3(60000.));
        if(depth<f.meta.y) {
            f.front.rgb+=f.front.a*emission*absorbed;
            f.front.a*=1.-absorbed;f.lens.z=min(f.lens.z,depth);
        } else {
            f.back.rgb+=f.back.a*emission*absorbed;
            f.back.a*=1.-absorbed;f.lens.w=min(f.lens.w,depth);
        }
    }
}
void horizonAbsorption(inout Field f,vec3 position) {
    float depth=length(position-(Eye-Center));
    if(depth>=f.meta.x) return;
    f.meta.z=1.;
    if(depth<f.meta.y) f.front.a=0.; else f.back.a=0.;
}

// Kept separate for the GPU analytic probes; callers can omit all material work.
// stateOut = (terminal u, terminal du/dphi, angle, termination):
// 0 escaped, 1 horizon, 2 finite orbit budget, 3 observer inside horizon.
vec4 traceSchwarzschild(inout Field f,vec3 direction,float mass,bool material) {
    vec3 origin=Eye-Center;
    float observerRadius=length(origin);
    // A static observer does not exist at/below the horizon. Fail dark and finite.
    if(observerRadius<=2.*mass) { if(material) horizonAbsorption(f,origin);return vec4(.5,0,0,3); }
    float u=mass/observerRadius;
    vec3 radial=origin/observerRadius;
    float cosine=clamp(dot(direction,radial),-1.,1.);
    vec3 across=direction-radial*cosine;float sine=length(across);
    if(sine<1e-7) across=normalize(cross(radial,abs(radial.y)<.9?vec3(0,1,0):vec3(1,0,0)));
    else across/=sine;
    float inverseImpact=u*sqrt(1.-2.*u)/max(sine,1e-8);
    vec2 state=vec2(u,-cosine*inverseImpact);
    float phi=0.;vec3 position=origin;
    // At most ~2.4 turns. RK4 angular steps also resolve photon-sphere dwell.
    for(int i=0;i<256;i++) {
        float h=min(.06,.04/max(abs(state.y),.001));
        vec2 next=geodesicStep(state,h);
        bool escaped=next.x<=0.,captured=next.x>=.5;
        if(escaped || captured) {
            // Refine the terminal event on the SAME orbit; never use a straight sphere mask.
            float lo=0.,hi=h;
            for(int j=0;j<12;j++) {
                float mid=(lo+hi)*.5;float value=geodesicStep(state,mid).x;
                if(escaped?value<=0.:value>=.5) hi=mid;else lo=mid;
            }
            h=(lo+hi)*.5;next=geodesicStep(state,h);
        }
        float angle=phi+h;
        vec3 er=radial*cos(angle)+across*sin(angle);
        vec3 endpoint=er*(mass/max(next.x,1e-8));
        // Outside the disk avoid gigantic escape chords and unnecessary texture reads.
        float diskBound=Outer+Thickness*2.;
        if(material && (length(position)<diskBound*1.1 || length(endpoint)<diskBound*1.1))
            relativisticSegment(f,position,endpoint,mass,observerRadius);
        if(captured) {
            if(material) horizonAbsorption(f,er*(2.*mass));
            return vec4(.5,next.y,angle,1);
        }
        if(escaped) {
            if(material) {
                vec4 projected=VP*vec4(er,0.);
                vec2 support=sphere(origin,direction,bodyExtent());
                // A screen-depth surface inside the lens is not a source at
                // infinity. Preserve it instead of applying the entire sky bend.
                if(f.meta.x>=support.y && projected.w>1e-6 && !any(isnan(projected)) && !any(isinf(projected))) {
                    f.lens.xy=projected.xy/projected.w*.5+.5;
                    f.meta.w=min(f.meta.x,max(max(support.x,0.),2.*mass));
                }
            }
            return vec4(0,next.y,angle,0);
        }
        state=next;phi=angle;position=endpoint;
    }
    // Unresolved near-critical orbit: retain accumulated light and reject the
    // unknown background, rather than leaking an untraced straight source.
    if(material) {
        float depth=length(position-origin);
        if(depth<f.meta.x) {
            if(depth<f.meta.y) f.front.a=0.;else f.back.a=0.;
            f.meta.z=2.;
        }
    }
    return vec4(state,phi,2);
}
Field schwarzschildField(vec2 uv) {
    vec3 direction=rayAt(uv),origin=Eye-Center;
    float opaque=distanceAt(uv,texture(OpaqueDepth,uv).r);
    float surface=distanceAt(uv,texture(SurfaceDepth,uv).r);
    if(surface>=opaque-max(.002,opaque*.00001)) surface=opaque;
    Field f;f.front=vec4(0,0,0,1);f.back=f.front;
    f.lens=vec4(uv,65504,65504);f.meta=vec4(opaque,surface,0,-1);
    vec2 bounds=sphere(origin,direction,bodyExtent());
    if(bounds.y<=0. || max(0.,bounds.x)>=opaque) return f;
    float mass=gravitationalRadius();
    if(mass<=1e-7) {
        // Consistent zero-mass limit: luminous disk, no horizon or gravitational lens.
        float start=max(0.,bounds.x),finish=min(bounds.y,opaque),stride=(finish-start)/96.;
        for(int i=0;i<96;i++) {
            vec3 a=origin+direction*(start+float(i)*stride);
            relativisticSegment(f,a,a+direction*stride,0.,max(length(origin),.001));
        }
    } else traceSchwarzschild(f,direction,mass,true);
    return f;
}
