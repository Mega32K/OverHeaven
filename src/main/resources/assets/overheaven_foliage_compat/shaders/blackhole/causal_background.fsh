#version 330 core
in vec2 texCoord;
layout(location=0) out vec4 SceneColor;
layout(location=1) out vec4 OpaqueColor;
uniform sampler2D Scene,Opaque,OpaqueDepth,SurfaceDepth,Transmission;
uniform mat4 VP,InverseVP;
uniform vec3 Eye,Center;
uniform float IntroBackgroundGain,IntroRadialImpact;
uniform int Samples;
vec3 pointAt(vec2 uv,float z) { vec4 p=InverseVP*vec4(uv*2.-1.,z*2.-1.,1.);return p.xyz/p.w; }
float distanceAt(vec2 uv,float z) { return z>=.9999999?1000000.:length(pointAt(uv,z)-Eye); }
bool layered(float opaque,float surface) { return surface<opaque-max(.002,opaque*.00001); }
// Validate the entire bilinear footprint. No edge texel clamping or hidden-world reconstruction.
bool sampleEnvironment(vec2 uv,float depth,float surface,bool transparent,out vec3 scene,out vec3 opaque) {
    ivec2 size=textureSize(Scene,0),base=ivec2(floor(uv*vec2(size)-.5));
    if(any(lessThan(base,ivec2(0))) || any(greaterThanEqual(base+1,size))) return false;
    vec2 fraction=fract(uv*vec2(size)-.5);scene=vec3(0);opaque=vec3(0);
    for(int y=0;y<2;y++) for(int x=0;x<2;x++) {
        ivec2 tap=base+ivec2(x,y);vec2 at=(vec2(tap)+.5)/vec2(size);
        float d=distanceAt(at,texelFetch(OpaqueDepth,tap,0).r);
        float s=distanceAt(at,texelFetch(SurfaceDepth,tap,0).r);
        if(d<max(2.,depth-max(.15,depth*.08))) return false;
        bool sourceLayer=layered(d,s);
        if(sourceLayer!=transparent || (transparent && abs(s-surface)>max(.15,surface*.06))) return false;
        float weight=(x==0?1.-fraction.x:fraction.x)*(y==0?1.-fraction.y:fraction.y);
        scene+=texelFetch(Scene,tap,0).rgb*weight;opaque+=texelFetch(Opaque,tap,0).rgb*weight;
    }
    return true;
}
void main() {
    vec4 host=texture(Scene,texCoord),base=texture(Opaque,texCoord);
    vec3 scene=host.rgb,opaque=base.rgb;
    if(IntroRadialImpact>0.) {
        vec4 center=VP*vec4(Center,1.);
        vec2 projected=center.xy/max(center.w,.00001)*.5+.5;
        if(center.w>.00001 && !any(isnan(center)) && !any(isinf(center)) &&
           all(greaterThan(projected,vec2(0))) && all(lessThan(projected,vec2(1)))) {
            vec2 size=vec2(textureSize(Scene,0)),radial=(texCoord-projected)*size;
            float d=distanceAt(texCoord,texture(OpaqueDepth,texCoord).r);
            float s=distanceAt(texCoord,texture(SurfaceDepth,texCoord).r);
            bool transparent=layered(d,s);
            float nearFade=smoothstep(2.,6.,min(d,s));
            float lengthPixels=48.*min(size.x,size.y)/1080.*IntroRadialImpact*nearFade;
            vec2 delta=-radial/max(length(radial),1.)*lengthPixels/size;
            // One shared budget for all taps; smoothly vanish before any footprint reaches an edge.
            vec2 margin=min(texCoord,1.-texCoord)-1.5/size;
            float budget=min(margin.x/(abs(delta.x)+2./size.x),margin.y/(abs(delta.y)+2./size.y));
            delta*=smoothstep(0.,1.,budget);
            vec3 sumScene=host.rgb,sumOpaque=base.rgb;float total=1.;
            for(int i=1;i<7;i++) {
                if(i>=Samples || lengthPixels<=.001) break;
                float t=float(i)/float(Samples-1),weight=1.-.55*t;
                vec3 a,b;
                if(!sampleEnvironment(texCoord+delta*t,d,s,transparent,a,b)) { a=host.rgb;b=base.rgb; }
                sumScene+=a*weight;sumOpaque+=b*weight;total+=weight;
            }
            opaque=sumOpaque/total;
            // Keep the host's glass/water surface and transmission exactly once at the destination.
            vec3 transmission=clamp(texture(Transmission,texCoord).rgb,0.,1.);
            scene=transparent?host.rgb+transmission*(opaque-base.rgb):sumScene/total;
        }
    }
    SceneColor=vec4(scene*IntroBackgroundGain,host.a);
    OpaqueColor=vec4(opaque*IntroBackgroundGain,base.a);
}
