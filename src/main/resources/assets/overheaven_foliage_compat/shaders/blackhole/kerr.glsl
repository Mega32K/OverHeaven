// Kerr null rays, G=c=M=1. Carter potentials and the observer tetrad follow
// James et al. (2015), arXiv:1502.03808, Appendix A.1 (A.1-A.4, A.11-A.12).
// Independently derived Mino-time form: u=1/r, u''=C*u+3*K*u^2-2*a^2*Q*u^3.
// The polar motion is a tangent vector on a unit sphere, avoiding pole divisions.
// Past rays use time reversal (a -> -a, L -> -L); disk velocities retain real a.
struct KerrConstants { float a,e,l,q,k,c,observerEnergy,observerRadius; };
struct KerrState { vec2 radial; vec3 n,w; float extra,azimuth; };
float highMass() { return gravitationalRadius()*bodyScale()*(IntroEnabled!=0?IntroCapture:1.); }
float kerrHorizon(float a) { return 1.+sqrt(max(0.,1.-a*a)); }
float kerrIsco(float a) {
    float z1=1.+pow(1.-a*a,1./3.)*(pow(1.+a,1./3.)+pow(1.-a,1./3.));
    float z2=sqrt(3.*a*a+z1*z1);
    float r=3.+z2-sign(a)*sqrt(max(0.,(3.-z1)*(3.+z1+2.*z2)));
    // Correct cancellation in the closed form near a=0 using its ISCO polynomial.
    for(int i=0;i<2;i++)r-=(r*r-6.*r+8.*a*sqrt(r)-3.*a*a)/(2.*r-6.+4.*a/sqrt(r));
    return r;
}
float kerrOrbit(float r,float a,float sense) { return sense/(pow(r,1.5)+sense*a); }
float kerrEmitterEnergy(float r,KerrConstants c) {
    float sense=Rotation<0.?-1.:1.,omega=kerrOrbit(r,-c.a,sense);
    float gtt=-(1.-2./r),gtp=2.*c.a/r,gpp=r*r+c.a*c.a*(1.+2./r);
    float ut=inversesqrt(max(1e-8,-gtt-2.*gtp*omega-gpp*omega*omega));
    return ut*(c.e+omega*c.l);
}
vec3 kerrLocal(vec3 p) { return vec3(dot(p,DiskRight),dot(p,DiskForward),dot(p,DiskNormal)); }
vec3 kerrWorld(vec3 p) { return DiskRight*p.x+DiskForward*p.y+DiskNormal*p.z; }
float kerrRadius(vec3 p,float a) {
    float t=dot(p,p)-a*a;
    return sqrt(max(.5*(t+sqrt(max(0.,t*t+4.*a*a*p.z*p.z))),1e-16));
}
vec3 kerrPosition(KerrState s,float a) {
    float r=1./max(s.radial.x,1e-9);
    float cs=cos(s.azimuth),sn=sin(s.azimuth);
    vec3 n=vec3(cs*s.n.x-sn*s.n.y,sn*s.n.x+cs*s.n.y,s.n.z);
    return n*vec3(sqrt(r*r+a*a),sqrt(r*r+a*a),r);
}
vec3 axial(vec3 p) { return vec3(-p.y,p.x,0.); }
KerrState kerrAdd(KerrState s,KerrState d,float h) {
    s.radial+=d.radial*h;s.n+=d.n*h;s.w+=d.w*h;s.extra+=d.extra*h;s.azimuth+=d.azimuth*h;return s;
}
KerrState kerrDerivative(KerrState s,KerrConstants c) {
    float u=max(s.radial.x,1e-9),v=s.radial.y;
    float d=max(1e-7,1.-2.*u+c.a*c.a*u*u);
    float drag=c.a*(2.*c.e*u-c.a*c.l*u*u)/d;
    KerrState t;
    // Keep the polynomial smooth across u=0 for RK's trial escape stages.
    float radialU=s.radial.x;
    t.radial=vec2(v,radialU*(c.c+radialU*(3.*c.k-2.*c.a*c.a*c.q*radialU)));
    // Axial dragging commutes with the axisymmetric polar evolution. Integrate
    // its phase separately, so BL's rapid near-horizon winding cannot exhaust
    // the angular step budget without making progress toward the horizon.
    t.n=s.w;t.azimuth=drag;
    t.w=-dot(s.w,s.w)*s.n+c.a*c.a*s.n.z*(vec3(0,0,1)-s.n.z*s.n);
    // Integrate t-(r_observer-r), not two huge, almost cancelling travel times.
    float radialTime=(c.e>0. && v>0.)?(-c.c-2.*c.k*u+c.a*c.a*c.q*u*u)/(1.+v):(c.e-v)/(u*u);
    t.extra=radialTime+c.a*c.a*c.e*s.n.z*s.n.z+c.a*c.l
        +(2.*c.e/u-c.a*c.l)*(1.+c.a*c.a*u*u)/d;
    // After leaving the material support, the remaining time to infinity is unused.
    if(v<0. && u<min(.015625,highMass()/max(Outer*bodyScale()+Thickness*2.,.001))) t.extra=0.;
    return t;
}
KerrState kerrStep(KerrState s,KerrConstants c,float h) {
    KerrState a=kerrDerivative(s,c),b=kerrDerivative(kerrAdd(s,a,h*.5),c);
    KerrState d=kerrDerivative(kerrAdd(s,b,h*.5),c),e=kerrDerivative(kerrAdd(s,d,h),c);
    s=kerrAdd(s,a,h/6.);s=kerrAdd(s,b,h/3.);s=kerrAdd(s,d,h/3.);return kerrAdd(s,e,h/6.);
}
bool kerrInitial(vec3 origin,vec3 direction,float a,out KerrState s,out KerrConstants c) {
    float r=kerrRadius(origin,a),r2=r*r,aa=a*a;
    s.n=normalize(origin/vec3(sqrt(r2+aa),sqrt(r2+aa),r));s.extra=0.;s.azimuth=0.;
    c.a=a;c.observerRadius=r;
    if(r<=kerrHorizon(a)*1.0001) return false;
    float sinTheta=length(s.n.xy),cosTheta=s.n.z;
    vec2 azimuth=sinTheta>1e-7?s.n.xy/sinTheta:vec2(1,0);
    vec3 ephi=vec3(-azimuth.y,azimuth.x,0.);
    vec3 etheta=vec3(cosTheta*azimuth,-sinTheta);
    vec3 er=normalize(vec3(r/sqrt(r2+aa)*s.n.xy,cosTheta));
    vec3 ethetaWorld=normalize(vec3(sqrt(r2+aa)*cosTheta*azimuth,-r*sinTheta));
    float radial=dot(direction,er),polar=dot(direction,ethetaWorld),az=dot(direction,ephi);
    float sigma=r2+aa*cosTheta*cosTheta,delta=r2-2.*r+aa;
    float area=(r2+aa)*(r2+aa)-aa*delta*sinTheta*sinTheta;
    float lapse=sqrt(sigma*delta/area),omega=2.*a*r/area;
    float lever=sqrt(area/sigma),energy=lapse+omega*lever*sinTheta*az;
    if(abs(energy)<1e-7) return false;
    c.e=sign(energy);c.observerEnergy=1./abs(energy);
    c.l=lever*sinTheta*az*c.observerEnergy;
    s.w=(sqrt(sigma)*polar*etheta+lever*az*ephi)*c.observerEnergy;
    c.q=dot(s.w,s.w)-c.l*c.l-aa*cosTheta*cosTheta;
    c.k=c.q+(c.l-a*c.e)*(c.l-a*c.e);c.c=aa-c.l*c.l-c.q;
    s.radial=vec2(1./r,-sqrt(delta*sigma)*radial/r2*c.observerEnergy);
    return true;
}

// Bounded two-cohort material time. CPU supplies a split double clock; GPU
// subtracts each sample's actual ray delay BEFORE choosing cohorts and hashing.
// A reset cohort has zero weight. Negative emission times are valid and deterministic.
float highCohortHash(float cycle,int cohort) {
    uint n=uint(mod(cycle,65536.))+uint(Seed*257.)*1664525u+uint(cohort)*1013904223u;
    n^=n>>16;n*=0x7feb352du;n^=n>>15;n*=0x846ca68bu;n^=n>>16;
    return float(n&0x00ffffffu)/16777216.;
}
// Widths in blocks along the local radial and tangential directions. An oblique
// chord averages gas along its own direction, not across every strand it touches.
vec2 diskFootprint(vec2 radial,vec2 stepXY,float pixelWidth) {
    return max(vec2(pixelWidth),abs(vec2(dot(radial,stepXY),dot(vec2(-radial.y,radial.x),stepXY)))*.5);
}
vec3 highCohort(vec2 uv,vec2 width,float offset) {
    vec2 seed=vec2(Seed*.071+offset,Seed*.137+offset*.731);
    vec3 eddy=diskLookup(uv*vec2(2,1)+seed,width*vec2(2,1));
    // Bend the strands within the gas. Integer repeats in both coordinates
    // keep the azimuth seam and every wrap of radial inflow continuous.
    float bent=uv.y+.105*(eddy.r-.5);
    float bentWidth=width.y+.44*(2.*width.x+width.y);
    vec2 ribbonWidth=vec2(3.*width.x,4.*bentWidth+width.x);
    vec3 ribbon=diskLookup(vec2(3.*uv.x,4.*bent+uv.x)-seed,ribbonWidth);
    vec2 fiberWidth=vec2(6.*width.x,9.*bentWidth+2.*width.x+1.5*max(ribbonWidth.x,ribbonWidth.y));
    vec3 fiber=diskLookup(vec2(6.*uv.x,9.*bent+2.*uv.x+.38*(ribbon.r-.5))+seed.yx,fiberWidth);
    float body=smoothstep(.34,.67,.78*ribbon.r+.22*eddy.r);
    float sheet=smoothstep(.38,.68,fiber.r+.24*(fiber.g-.64));
    // A narrow ridge in the bent gas coordinate breaks up into filaments.
    // Return its unresolved contrast to the mean before the ridge test aliases.
    float ridge=1.-smoothstep(.018,.12,abs(fiber.r-.54));
    ridge=mix(ridge,.4,smoothstep(.018,.075,max(fiberWidth.x,fiberWidth.y)));
    float filament=mix(sheet,ridge,.62);
    return vec3(.06+.38*body+.18*filament, .08+.35*body+.35*body*body+(.28+1.6*body)*filament, eddy.r);
}
vec3 highMaterial(vec2 radial,float r,vec2 footprint,float delay,float mass) {
    const float turns=1./6.28318530718;
    float scale=bodyScale(),inner=Inner*scale,span=max((Outer-Inner)*scale,.001);
    r=max(r,.001);
    float sense=Rotation<0.?-1.:1.,a=clamp(Spin,-.95,.95)*sense;
    float q=pow(max(r/max(mass,1e-7),.001),1.5);
    float shear=mass>1e-7?(pow(6.,1.5)+a)/(q+a):pow(inner/r,1.5);
    float shearWidth=1.5*shear*(mass>1e-7?q/(q+a):1.)*footprint.x*turns/r;
    vec4 clock=DiskClock;
    if(DiskRenewal==0) clock=vec4(mod(Time,8.),mod(floor(Time/8.),65536.),fract(floor(Time/8.)*8.*Rotation*turns),fract(Time*Infall/max(Outer-Inner,.001)));
    float local=clock.x-delay,flow=fract(clock.w-delay*Infall/max(Outer-Inner,.001));
    float introFlow=IntroEnabled!=0?IntroFlow:0.;
    vec2 uv=vec2(atan(radial.y,radial.x)*turns,(r-inner)/span+flow+introFlow*.85);
    vec2 width=vec2(footprint.y*turns/r,footprint.x/span);
    if(Rotation==0.) return highCohort(uv-vec2(introFlow*1.7*shear*turns,0.),width+vec2(abs(introFlow*1.7)*shearWidth,0.),0.);
    float cycle0=floor((local+4.)/8.),cycle1=floor(local/8.);
    float age0=local-cycle0*8.,age1=local-(cycle1*8.+4.);
    vec2 phase=vec2(age0,age1)*Rotation+introFlow*1.7;
    vec2 anchor=fract(clock.z+vec2(cycle0*8.,cycle1*8.+4.)*Rotation*turns);
    vec2 azimuth=uv.x-phase*(shear*turns)-anchor;
    vec2 azimuthWidth=width.x+abs(phase)*shearWidth;
    float blend=pow(cos(3.14159265359*age0/8.),2.);
    vec3 first=highCohort(vec2(azimuth.x,uv.y),vec2(azimuthWidth.x,width.y),highCohortHash(clock.y+cycle0,0));
    vec3 second=highCohort(vec2(azimuth.y,uv.y),vec2(azimuthWidth.y,width.y),highCohortHash(clock.y+cycle1,1));
    return mix(second,first,blend);
}
vec3 highEmission(float radius,float inner,float outer,vec3 material) {
    float q=inner/radius,peakQ=inner/min(outer,inner*(49./36.));
    float flux=q*q*q*(1.-sqrt(q))/max(peakQ*peakQ*peakQ*(1.-sqrt(peakQ)),1e-9);
    float temperature=6500.*sqrt(sqrt(max(flux,0.)))*(.91+.09*min(material.y,1.6));
    vec3 grade=mix(ColdColor,HotColor,smoothstep(.14,.94,flux));
    // White-balance the 16-band integral at 6500 K so the peak takes the
    // authored hot color; cooler gas keeps its thermal hue. Disk exposure only.
    const float filmExposure=2.2;
    vec3 spectrum=thermalRadiance(temperature)/vec3(.1532,.1404,.1482);
    vec3 emission=spectrum*grade*(Brightness*material.y*filmExposure);
    if(IntroEnabled!=0) emission*=1.+IntroCharge*.55+IntroTighten*2.8;
    return min(emission,vec3(60000.));
}
void highAccumulate(inout Field f,vec3 emission,float absorbed,float depth) {
    if(depth<f.meta.y) { f.front.rgb+=f.front.a*emission*absorbed;f.front.a*=1.-absorbed;f.lens.z=min(f.lens.z,depth); }
    else { f.back.rgb+=f.back.a*emission*absorbed;f.back.a*=1.-absorbed;f.lens.w=min(f.lens.w,depth); }
}

float gasHalfHeight(float thickness) {
    return 1.45*thickness*inversesqrt(1.+(IntroEnabled!=0?1.5*IntroTighten:0.));
}
void highGasSample(inout Field f,vec2 radial,float radius,float inner,float outer,float thickness,
                   vec2 footprint,float delay,float mass,float lengthInGas,vec2 vertical,float depth) {
    vec3 material=highMaterial(radial,radius,footprint,delay,mass);
    float edge=smoothstep(inner,inner+(outer-inner)*.04,radius)
              *(1.-smoothstep(max(inner+(outer-inner)*.5,outer*.74),outer,radius));
    // Exact mean of the vertical profiles 1-z² and (1-z²)² across this
    // clipped subsegment. Adjacent RK steps can split the layer anywhere.
    float z2=vertical.x*vertical.x,d2=vertical.y*vertical.y,profile=1.-z2;
    float compact=profile*profile+(2.*z2-2./3.)*d2+.2*d2*d2;
    float density=max(0.,mix(compact,profile-d2/3.,.15*material.z));
    float optical=edge*material.x*density*lengthInGas/max(thickness,.02)*1.4;
    highAccumulate(f,highEmission(radius,inner,outer,material),1.-exp(-optical),depth);
}
void kerrGas(inout Field f,vec3 a,vec3 b,KerrState start,KerrState finish,KerrConstants c,float mass,float inner) {
    float scale=bodyScale();
    float outer=Outer*scale,thickness=Thickness*scale;
    if(outer<=inner) return;
    float halfHeight=gasHalfHeight(thickness);
    float begin=0.,end=1.,delta=b.z-a.z;
    if(abs(delta)<1e-9) { if(abs(a.z)>halfHeight) return; }
    else { vec2 slab=(vec2(-halfHeight,halfHeight)-a.z)/delta;begin=max(0.,min(slab.x,slab.y));end=min(1.,max(slab.x,slab.y)); }
    if(end<=begin) return;
    vec3 stepVector=(b-a)*((end-begin)*.5);
    float segmentLength=length(stepVector);
    for(int i=0;i<2;i++) {
        float t=mix(begin,end,(float(i)+.5)*.5);
        vec3 p=mix(a,b,t),world=kerrWorld(p),eye=Eye-Center;
        float depth=length(world-eye);if(depth>=f.meta.x) continue;
        float r=kerrRadius(p/max(mass,1e-7),c.a),radius=mass*r;
        if(radius<=inner || radius>=outer) continue;
        float delay=max(0.,mass*(c.observerRadius-r+mix(start.extra,finish.extra,t)))/80.;
        vec2 radial=p.xy/max(length(p.xy),1e-8);
        highGasSample(f,radial,radius,inner,outer,thickness,diskFootprint(radial,stepVector.xy,depth*PixelAngle),
                      delay,mass,segmentLength,vec2(p.z,.5*stepVector.z)/halfHeight,depth);
    }
}

// Returns (termination, angular path, radial invariant error, last travel time/M).
// A 512-step budget resolves repeated disk intersections without artificial rings.
vec4 traceKerr(inout Field f,vec3 direction,float mass,bool material) {
    KerrState s;KerrConstants c;
    vec3 origin=kerrLocal(Eye-Center)/mass;
    if(!kerrInitial(origin,kerrLocal(direction),-clamp(Spin,-.95,.95),s,c)) {
        if(material) horizonAbsorption(f,Eye-Center);return vec4(3,0,0,0);
    }
    float limit=1./(kerrHorizon(c.a)*1.001),angle=0.,error=0.;
    float inner=max(Inner*bodyScale(),mass*kerrIsco((Rotation<0.?-1.:1.)*Spin));
    vec3 position=kerrPosition(s,c.a)*mass;
    for(int i=0;i<512;i++) {
        KerrState d=kerrDerivative(s,c);
        float h=min(.06/max(length(d.n),.05),.035/max(abs(s.radial.y),.01));
        // Logarithmic far-inbound steps resolve coordinate time as well as bending.
        if(s.radial.y>0.) h=min(h,.25*s.radial.x/max(s.radial.y,.001));
        // An outward ray already inside the disk must not jump straight to infinity,
        // including the small-mass limit where the whole disk has tiny u=M/r.
        if(length(position)<(Outer+Thickness*2.)*bodyScale()*1.1)
            h=min(h,.2*s.radial.x/max(abs(s.radial.y),.001));
        // Stay outside the BL coordinate singularity while locating absorption.
        if(s.radial.y>0.) h=min(h,.6*(1./kerrHorizon(c.a)-s.radial.x)/max(s.radial.y,.001));
        KerrState next=kerrStep(s,c,h);
        bool escaped=next.radial.x<=0.,captured=next.radial.x>=limit;
        if(escaped || captured) {
            float lo=0.,hi=h;
            for(int j=0;j<10;j++) {
                float mid=(lo+hi)*.5,value=kerrStep(s,c,mid).radial.x;
                if(escaped?value<=0.:value>=limit)hi=mid;else lo=mid;
            }
            h=(lo+hi)*.5;next=kerrStep(s,c,h);
        }
        next.n=normalize(next.n);next.w-=next.n*dot(next.n,next.w);
        vec3 endpoint=kerrPosition(next,c.a)*mass;
        float support=Outer*bodyScale()+Thickness*bodyScale()*2.;
        if(material && !escaped && (length(position)<support*1.1 || length(endpoint)<support*1.1)) kerrGas(f,position,endpoint,s,next,c,mass,inner);
        angle+=h*length(d.n+d.azimuth*axial(s.n));
        float u=next.radial.x,expected=1.+u*u*(c.c+u*(2.*c.k-c.a*c.a*c.q*u));
        error=max(error,abs(next.radial.y*next.radial.y-expected));
        if(captured) {
            if(material) horizonAbsorption(f,kerrWorld(endpoint));
            return vec4(1,angle,error,c.observerRadius-1./next.radial.x+next.extra);
        }
        if(escaped) {
            if(material && c.e>0.) {
                // Use the closest point on the viewing ray as a thin lens for host scenery.
                // The actual null ray and all disk/horizon intersections stay untouched.
                float pivot=max(dot(Center-Eye,direction),mass*kerrHorizon(c.a));
                float impact=length(cross(Eye-Center,direction)),extent=bodyExtent();
                float weight=1.-smoothstep(.75*extent,extent,impact);
                vec3 outgoing=mix(direction,kerrWorld(normalize(endpoint)),weight);
                vec4 projected=VP*vec4(outgoing,0.);
                if(f.meta.x>pivot && projected.w>1e-6 && !any(isnan(projected)) && !any(isinf(projected))) {
                    f.lens.xy=projected.xy/projected.w*.5+.5;
                    f.meta.w=pivot;
                }
            }
            return vec4(0,angle,error,0);
        }
        s=next;position=endpoint;
    }
    if(material) { horizonAbsorption(f,kerrWorld(position));if(f.meta.z>0.)f.meta.z=2.; }
    return vec4(2,angle,error,c.observerRadius-1./s.radial.x+s.extra);
}
Field kerrField(vec2 uv) {
    vec3 direction=rayAt(uv),origin=Eye-Center;
    float opaque=distanceAt(uv,texture(OpaqueDepth,uv).r),surface=distanceAt(uv,texture(SurfaceDepth,uv).r);
    if(surface>=opaque-max(.002,opaque*.00001))surface=opaque;
    Field f;f.front=vec4(0,0,0,1);f.back=f.front;f.lens=vec4(uv,65504,65504);f.meta=vec4(opaque,surface,0,-1);
    vec2 bounds=sphere(origin,direction,bodyExtent());if(bounds.y<=0. || max(0.,bounds.x)>=opaque)return f;
    float mass=highMass();
    if(mass>1e-7) {
        // Foreground in front of the lens plane has no background deflection.
        // A camera-centred distance bound also excludes every gas/horizon hit,
        // including the oblate disk and the entire trapped-photon region.
        float matterExtent=max((Outer+2.*Thickness)*bodyScale()+mass*abs(Spin),8.*mass);
        if(opaque<=dot(-origin,direction) && length(origin)>opaque+matterExtent) return f;
        traceKerr(f,direction,mass,true);
    }
    else {
        // Zero mass: same high material/exposure and light clock, no horizon,
        // frequency shift, orbital speed or gravity. Clip the thin slab first.
        vec3 localOrigin=kerrLocal(origin),localDirection=kerrLocal(direction);
        float thickness=Thickness*bodyScale(),inner=Inner*bodyScale(),outer=Outer*bodyScale();
        float halfHeight=gasHalfHeight(thickness);
        float start=max(0.,bounds.x),finish=min(bounds.y,opaque);
        if(abs(localDirection.z)<1e-8) {if(abs(localOrigin.z)>halfHeight)return f;}
        else {vec2 slab=(vec2(-halfHeight,halfHeight)-localOrigin.z)/localDirection.z;start=max(start,min(slab.x,slab.y));finish=min(finish,max(slab.x,slab.y));}
        if(finish<=start)return f;
        float stride=(finish-start)/96.;
        for(int i=0;i<96;i++) {
            float depth=start+(float(i)+.5)*stride;vec3 p=localOrigin+localDirection*depth;
            float radius=length(p.xy);if(radius<=inner || radius>=outer)continue;
            vec2 radial=p.xy/radius;
            highGasSample(f,radial,radius,inner,outer,thickness,diskFootprint(radial,localDirection.xy*stride,depth*PixelAngle),
                          depth/80.,0.,stride,vec2(p.z,.5*localDirection.z*stride)/halfHeight,depth);
        }
    }
    return f;
}
