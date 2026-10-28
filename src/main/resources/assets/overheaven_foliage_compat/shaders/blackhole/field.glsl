uniform mat4 InverseVP;
uniform mat4 VP;
uniform vec3 Eye;
uniform vec3 Center;
uniform vec3 DiskNormal;
uniform vec3 DiskRight;
uniform vec3 DiskForward;
uniform vec3 HotColor;
uniform vec3 ColdColor;
uniform float Radius;
uniform float Inner;
uniform float Outer;
uniform float Thickness;
uniform float Brightness;
uniform float Rotation;
uniform float Infall;
uniform float Lensing;
uniform float Glow;
uniform float Time;
uniform float Seed;
uniform float FinaleAmount;
uniform float FinaleProgress;
uniform float FinaleTime;
uniform float FinalePulse;
uniform int FinalePhase;
float coreRadius() { return Radius*(1.0-clamp(FinaleAmount,0.0,1.0)); }
uniform int Steps;
uniform float PixelAngle;
uniform sampler2D DiskTexture;
uniform sampler2D OpaqueDepth;
uniform sampler2D SurfaceDepth;

vec3 innerTransitionColor(vec3 color, float radialDistance) {
    float fraction=clamp((radialDistance-Radius)/max(Inner-Radius,0.0001),0.0,1.0);
    float saturation=0.5*fraction*fraction*(3.0-2.0*fraction);
    float luminance=dot(color,vec3(0.2126,0.7152,0.0722));
    return mix(vec3(luminance),color,saturation);
}

vec3 pointAt(vec2 uv, float depth) {
    vec4 point = InverseVP * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return point.xyz / point.w;
}
vec3 rayAt(vec2 uv) { return normalize(pointAt(uv, 0.99) - Eye); }
float distanceAt(vec2 uv, float depth) { return depth >= 0.9999999 ? 1000000.0 : length(pointAt(uv, depth) - Eye); }
vec2 sphere(vec3 origin, vec3 direction, float radius) {
    float along = dot(origin, direction);
    float discriminant = along * along - dot(origin, origin) + radius * radius;
    if (discriminant < 0.0) return vec2(-1.0);
    float root = sqrt(discriminant);
    return vec2(-along - root, -along + root);
}
// Independently authored material. Integer angular repeats keep the atan seam continuous.
// Explicit mip footprints are valid inside divergent ray/slab loops (implicit derivatives are not).
vec3 diskLookup(vec2 coordinate, vec2 footprint) {
    vec2 texels = footprint * vec2(textureSize(DiskTexture, 0));
    float lod = max(0.0, log2(max(max(texels.x, texels.y), 1.0)));
    return textureLod(DiskTexture, coordinate, lod).rgb;
}
vec3 diskMaterial(vec2 disk, float footprint) {
    float radius = max(length(disk), 0.001);
    float span = max(Outer - Inner, 0.001);
    float orbit = atan(disk.y, disk.x) / 6.28318530718;
    orbit -= Time * Rotation * pow(Inner / radius, 1.5) / 6.28318530718;
    float flow = (radius - Inner + Time * Infall) / span;
    vec2 base = vec2(orbit, flow);
    vec2 width = vec2(footprint / (6.28318530718 * radius), footprint / span);
    // Include radial orbital shear in the LOD; long-running animation must not shimmer.
    width.x += abs(Time*Rotation)*pow(Inner/radius,1.5)*1.5*footprint/(6.28318530718*radius);
    vec2 offset = vec2(Seed * 0.071, Seed * 0.137);
    vec3 clouds = diskLookup(base * vec2(5.0, 1.0) + offset, width * vec2(5.0, 1.0));
    vec3 folds = diskLookup(base * vec2(11.0, 3.0) - offset, width * vec2(11.0, 3.0));
    vec3 grain = diskLookup(base * vec2(23.0, 6.0) + offset.yx, width * vec2(23.0, 6.0));
    vec3 filaments = diskLookup(base * vec2(3.0, 7.0) + vec2(clouds.g*0.12,folds.b*0.08) + offset,
        width * vec2(6.0, 10.0));
    float clumps = smoothstep(0.32,0.68,clouds.r*0.8+folds.g*0.2);
    float threads = smoothstep(0.25,0.78,filaments.r);
    float coverage = 0.025 + pow(clumps,1.5)*(0.25+threads*1.25)*(0.55+grain.b*0.7);
    float radiation = 0.025 + pow(clumps,2.0)*(0.35+threads*2.2)*(0.4+folds.r*1.2);
    return vec3(coverage, radiation, clouds.b);
}
// Environment deflection is independent of disk opacity and integration steps.
// Integrate the softened transverse inverse-square force along the visible chord.
// This is a weak-field artistic direction map, not a world-surface intersection.
vec3 environmentLens(vec2 uv,vec3 direction,float opaque,vec2 bounds) {
    if(Lensing<=0.00001 || length(Center-Eye)<=Radius || bounds.y<=0.0) return vec3(uv,-1.0);
    float first=max(bounds.x,0.0), last=min(bounds.y,opaque);
    if(last<=first) return vec3(uv,-1.0);
    vec3 mass=Center-Eye;
    float along=dot(mass,direction);
    vec3 transverse=mass-direction*along;
    float impact=length(transverse);
    if(impact<0.00001) return vec3(uv,-1.0);
    float extent=Outer*1.35+Thickness*2.0;
    float window=1.0-smoothstep(extent*0.72,extent,impact);
    // Keep the inner lens intact; gently reduce outer deflection by up to 35%.
    window*=1.0-0.35*smoothstep(extent*0.35,extent*0.80,impact);
    float softened=impact*impact+Radius*Radius*0.0625;
    float a=first-along, b=last-along;
    float integral=b/sqrt(softened+b*b)-a/sqrt(softened+a*a);
    float angle=min(1.2,0.4*Radius*Lensing*impact/softened*max(0.0,integral)*window);
    angle*=mix(1.0,0.42*smoothstep(0.0,Radius*0.8,impact),FinaleAmount);
    vec3 outgoing=direction*cos(angle)+(transverse/impact)*sin(angle);
    // w=0 projects a direction, with no arbitrary sky plane or near/far cutoff.
    vec4 projected=VP*vec4(outgoing,0.0);
    if(projected.w<=0.00001 || any(isnan(projected)) || any(isinf(projected))) return vec3(uv,-1.0);
    vec2 source=projected.xy/projected.w*0.5+0.5;
    // Guard nearby source geometry even when the eye is inside the support sphere.
    float guard=min(opaque,max(first,Radius));
    return vec3(source,guard);
}
struct Field { vec4 front; vec4 back; vec4 lens; vec4 meta; };
Field ordinaryField(vec2 uv) {
    vec3 direction = rayAt(uv), origin = Eye - Center;
    float opaque = distanceAt(uv, texture(OpaqueDepth, uv).r);
    float surface = distanceAt(uv, texture(SurfaceDepth, uv).r);
    bool transparent = surface < opaque - max(0.002, opaque * 0.00001);
    if (!transparent) surface = opaque;
    vec2 bounds = sphere(origin, direction, Outer * 1.35 + Thickness * 2.0);
    vec2 core = sphere(origin, direction, max(coreRadius(),0.000001));
    float coreHit = core.y > 0.0 ? max(0.0, core.x) : 1000000.0;
    Field result;
    result.front = vec4(0.0,0.0,0.0,1.0); result.back = result.front;
    result.lens = vec4(uv,65504.0,65504.0);
    // meta.w is the minimum camera distance of a valid background source, or -1.
    result.meta = vec4(opaque, surface, coreHit < opaque ? 1.0 : 0.0, -1.0);
    if (bounds.y <= 0.0 || max(bounds.x, 0.0) >= opaque) return result;
    if(coreHit>=opaque) {
        vec3 mapping=environmentLens(uv,direction,opaque,bounds);
        result.lens.xy=mapping.xy; result.meta.w=mapping.z;
    }
    float travel = max(bounds.x, 0.0);
    vec3 position = origin + direction * travel;
    vec3 velocity = direction;
    float finish = min(bounds.y, min(opaque, coreHit));
    float budget = max(0.0001, finish - travel) / float(Steps);
    for (int sampleIndex = 0; sampleIndex < 96; sampleIndex++) {
        if (sampleIndex >= Steps || travel >= finish || result.front.a * result.back.a < 0.003) break;
        float radial = length(position);
        float stepSize = budget;
        stepSize = min(stepSize, finish - travel);
        vec3 acceleration = -position * (0.4 * Radius * Lensing / pow(max(radial, Radius * 0.8), 3.0));
        acceleration -= velocity * dot(acceleration, velocity);
        vec3 nextVelocity = normalize(velocity + acceleration * stepSize);
        vec3 nextPosition = position + normalize(velocity + nextVelocity) * stepSize;
        float height0 = dot(position, DiskNormal), height1 = dot(nextPosition, DiskNormal);
        float delta = height1 - height0;
        float begin = 0.0, end = 1.0;
        if (abs(delta) < 0.00001) { if (abs(height0) > Thickness * 2.0) end = 0.0; }
        else {
            vec2 slab = vec2(-Thickness * 2.0 - height0, Thickness * 2.0 - height0) / delta;
            begin = max(0.0, min(slab.x, slab.y)); end = min(1.0, max(slab.x, slab.y));
        }
        if (end > begin) {
            // Two quadrature taps resolve long grazing spans; thin slabs cannot fall between steps.
            float subLength = stepSize * (end-begin) * 0.5;
            for (int gasTap=0; gasTap<2; gasTap++) {
                float midpoint = mix(begin, end, (float(gasTap)+0.5)*0.5);
                vec3 gas = mix(position, nextPosition, midpoint);
                vec2 disk = vec2(dot(gas, DiskRight), dot(gas, DiskForward));
                float radius = length(disk);
                if (radius <= Inner || radius >= Outer) continue;
                float radialMask = smoothstep(Inner, Inner + (Outer-Inner)*0.04, radius) * (1.0-smoothstep(Outer*0.74, Outer, radius));
                float grazing = sqrt(max(0.0,1.0-pow(dot(velocity,DiskNormal),2.0)));
                float footprint = max(max(travel,1.0)*PixelAngle, subLength*grazing*0.35);
                vec3 material = diskMaterial(disk,footprint);
                float height = dot(gas,DiskNormal)/max(Thickness,0.001);
                float vertical = exp(-height*height*(0.55+material.z*0.35));
                float optical = radialMask * material.x * vertical * subLength / max(Thickness,0.02);
                float absorption = 1.0-exp(-optical*1.4);
                float heat = pow(clamp((Outer-radius)/(Outer-Inner),0.0,1.0),1.35);
                vec3 emission = mix(ColdColor,HotColor,smoothstep(0.10,0.74,heat)) * Brightness * (0.16+heat*3.3);
                emission *= material.y * 0.5;
                float doppler = 1.0+0.28*dot(normalize(cross(DiskNormal,gas)),direction);
                float segmentDistance = travel+midpoint*stepSize;
                float subBegin = travel+stepSize*mix(begin,end,float(gasTap)*0.5);
                float frontFraction = clamp((surface-subBegin)/max(subLength,0.00001),0.0,1.0);
                float frontAbsorb = 1.0-pow(1.0-absorption,frontFraction);
                float backAbsorb = 1.0-pow(1.0-absorption,1.0-frontFraction);
                result.front.rgb += result.front.a*emission*frontAbsorb*doppler;
                result.front.a *= 1.0-frontAbsorb;
                result.back.rgb += result.back.a*emission*backAbsorb*doppler;
                result.back.a *= 1.0-backAbsorb;
                if(frontAbsorb>0.00001) result.lens.z=min(result.lens.z,segmentDistance);
                if(backAbsorb>0.00001) result.lens.w=min(result.lens.w,segmentDistance);
            }
        }
        if (Glow > 0.0) {
            for (int hazeTap=0; hazeTap<2; hazeTap++) {
                float hazeFraction=(float(hazeTap)+0.5)*0.5;
                vec3 hazePosition=mix(position,nextPosition,hazeFraction);
                float hazeHeight=dot(hazePosition,DiskNormal);
                float hazeRadius=length(hazePosition-DiskNormal*hazeHeight);
                float hazeWidth=max(Radius*0.35,Thickness*3.0);
                vec2 hazeOffset=vec2(hazeRadius-Inner,hazeHeight*1.6)/hazeWidth;
                float hazeSquared=dot(hazeOffset,hazeOffset);
                if(hazeSquared>=6.25) continue;
                float hazeDensity=exp(-hazeSquared*1.8)*(1.0-smoothstep(2.25,6.25,hazeSquared));
                float hazeLength=stepSize*0.5;
                float hazeStart=travel+float(hazeTap)*hazeLength;
                float hazeFront=clamp((surface-hazeStart)/max(hazeLength,0.00001),0.0,1.0);
                vec3 hazeLight=innerTransitionColor(HotColor,hazeRadius)*Brightness*Glow*0.11*hazeDensity*hazeLength/hazeWidth;
                result.front.rgb+=result.front.a*hazeLight*hazeFront;
                result.back.rgb+=result.back.a*hazeLight*(1.0-hazeFront);
                if(hazeDensity>0.0001 && hazeFront>0.0) result.lens.z=min(result.lens.z,hazeStart);
                if(hazeDensity>0.0001 && hazeFront<1.0) result.lens.w=min(result.lens.w,hazeStart);
            }
        }
        travel += stepSize; position = nextPosition; velocity = nextVelocity;
    }
    return result;
}

// Singularity radiance has the same opaque/transparent segmentation as the ordinary disk.
void finaleLight(inout Field f,vec3 light,float distance,float opaque,float surface) {
    if(distance<0.0 || distance>=opaque || max(light.r,max(light.g,light.b))<0.000001) return;
    if(distance<surface) { f.front.rgb+=light; f.lens.z=min(f.lens.z,distance); }
    else { f.back.rgb+=light; f.lens.w=min(f.lens.w,distance); }
}
float finaleRadius() {
    float contraction=FinalePhase==4?smoothstep(0.,1.,FinaleProgress):FinalePhase>4?1.:0.;
    return max(Radius*mix(1.,.32,contraction),.001);
}
float finaleEmission() {
    if(FinalePhase==3) return mix(1.,.17,smoothstep(.35,1.,FinaleProgress));
    if(FinalePhase==4) return mix(.17,.025,smoothstep(0.,1.,FinaleProgress));
    if(FinalePhase==5) return mix(.025,.0015,smoothstep(0.,1.,FinaleProgress));
    if(FinalePhase==6) return .0015*(1.-smoothstep(.35,.625,FinaleProgress));
    if(FinalePhase==7) return 0.;
    return 1.;
}
vec3 finaleLocal(vec3 position) { return vec3(dot(position,DiskRight),dot(position,DiskNormal),dot(position,DiskForward))/finaleRadius(); }
// Original world-space thin-film palette. No screen-space radial texture or reference code.
vec3 finaleFilm(float thickness,float incidence) {
    vec3 wavelength=vec3(0.650,0.510,0.440);
    vec3 interference=.5+.5*cos((thickness*(.4+.6*incidence))/wavelength*6.2831853);
    interference*=interference;
    return mix(vec3(.42,.50,.65),interference,.87);
}
void finaleMembranes(inout Field f,vec3 origin,vec3 ray,float closest,float opaque,float surface,float fade) {
    // A bounded far hemisphere makes the porous veil a background to the luminous center.
    // Texture coordinates remain in the disk's world basis when the camera turns or moves.
    float sourceRadius=finaleRadius();
    vec2 range=sphere(origin,ray,sourceRadius*1.5);
    float lo=max(max(range.x,0.),closest+sourceRadius*.10),hi=min(range.y,opaque);
    if(hi>lo) {
        int count=Steps<=24?12:Steps<=48?20:28;
        float stride=(hi-lo)/float(count);
        for(int i=0;i<28;i++) {
            if(i>=count) break;
            float t=lo+(float(i)+.5)*stride;
            vec3 p=finaleLocal(origin+ray*t),q=p*3.15;
            float time=FinaleTime*.21+Seed*.017;
            // Unequal twists and a second, weaker fold create connected cavities rather than rings.
            float twist=.43*sin(p.y*1.7-time)+.27*p.y;
            q.xz=mat2(cos(twist),-sin(twist),sin(twist),cos(twist))*q.xz;
            q+=.31*sin(q.yzx*1.36+vec3(time,-time*.71,time*.43));
            q+=.11*sin(q.zxy*2.73-vec3(time*.6));
            float sheet=dot(sin(q),cos(q.yzx));
            float thickness=max(.13,stride/sourceRadius*3.5);
            float membrane=exp(-sheet*sheet/(thickness*thickness))*.13/thickness;
            float envelope=(1.-smoothstep(1.04,1.5,length(p)))*smoothstep(.18,.5,length(p));
            float folds=.32+.68*pow(.5+.5*sin(q.x-q.z+time),2.);
            vec3 tint=finaleFilm(.52+.30*sin(q.y+q.z*.7-time)+.16*p.x,.7);
            vec3 light=tint*membrane*envelope*folds*stride/sourceRadius*Brightness*.78*fade;
            finaleLight(f,light,t,opaque,surface);
        }
    }
    // Three overlapping small membranes form one chromatic center, not orbiting satellites.
    // Analytic intersections keep the thin fronts stable even at the low sample budget.
    for(int cell=0;cell<3;cell++) {
        vec3 localOffset=cell==0?vec3(-.055,.022,.024):cell==1?vec3(.054,-.034,.006):vec3(.008,.057,-.038);
        vec3 offset=(DiskRight*localOffset.x+DiskNormal*localOffset.y+DiskForward*localOffset.z)*sourceRadius;
        float size=sourceRadius*(cell==0?.200:cell==1?.175:.145);
        vec2 hits=sphere(origin-offset,ray,size);
        if(hits.y<=0.) continue;
        for(int side=0;side<2;side++) {
            float t=side==0?hits.x:hits.y;
            if(t<0. || t>=opaque) continue;
            vec3 normal=normalize(origin+ray*t-offset),p=finaleLocal(origin+ray*t);
            float incidence=abs(dot(normal,ray));
            float flow=sin(dot(p,vec3(16,23,-11))+FinaleTime*.44)+.5*sin(dot(p,vec3(-31,12,18))-FinaleTime*.27);
            float patches=smoothstep(-.65,.55,flow);
            float rim=pow(1.-incidence,2.3);
            vec3 tint=finaleFilm(.46+.16*flow+.045*float(cell),incidence);
            vec3 light=tint*(.10+rim*1.8)*patches*Brightness*2.1*fade;
            light+=vec3(1.,.985,.97)*pow(patches,5.)*pow(rim,3.)*Brightness*1.7*fade;
            finaleLight(f,light,t,opaque,surface);
        }
    }
}
#include "finale_energy.glsl"
Field finaleField(vec2 uv) {
    vec3 direction=rayAt(uv),origin=Eye-Center;
    float opaque=distanceAt(uv,texture(OpaqueDepth,uv).r),surface=distanceAt(uv,texture(SurfaceDepth,uv).r);
    if(surface>=opaque-max(.002,opaque*.00001)) surface=opaque;
    Field f; f.front=vec4(0,0,0,1); f.back=f.front; f.lens=vec4(uv,65504,65504); f.meta=vec4(opaque,surface,0,-1);
    float closest=-dot(origin,direction),impact=length(origin+direction*max(closest,0.));
    float fade=finaleEmission();
    finaleMembranes(f,origin,direction,closest,opaque,surface,fade);
    float pointWidth=max(finaleRadius()*.021,max(closest,1.)*PixelAngle*.75);
    float point=exp(-impact*impact/(pointWidth*pointWidth));
    // Finite corona feeds the existing HDR pyramid; high brightness is confined to the focus.
    float coronaWidth=max(finaleRadius()*.095,max(closest,1.)*PixelAngle);
    float corona=exp(-impact*impact/(coronaWidth*coronaWidth));
    vec3 pointLight=vec3(1.,.985,.965)*(point*(14.+FinalePulse*22.)+corona*(.58+FinalePulse*1.8))*Brightness*fade;
    finaleLight(f,pointLight,closest,opaque,surface);
    vec2 bounds=sphere(origin,direction,Outer*1.35+Thickness*2.);
    if(bounds.y>0. && max(bounds.x,0.)<opaque) {
        vec3 mapping=environmentLens(uv,direction,opaque,bounds); f.lens.xy=mapping.xy; f.meta.w=mapping.z;
    }
    finaleEnergy(f,origin,direction,opaque,surface,fade);
    return f;
}
Field integrate(vec2 uv) {
    if(FinaleAmount<=.00001) return ordinaryField(uv);
    Field show=finaleField(uv);
    if(FinaleAmount>=.99999) return show;
    Field base=ordinaryField(uv);
    base.front=mix(base.front,show.front,FinaleAmount); base.back=mix(base.back,show.back,FinaleAmount);
    base.lens=mix(base.lens,show.lens,FinaleAmount);
    return base;
}
