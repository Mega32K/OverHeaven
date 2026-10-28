#version 330 core
in vec3 World;
in vec2 Local;
flat in int Kind;
flat in vec3 Tangent,Bitangent;
flat in float ShardSeed;
layout(location=0) out vec4 Front;
layout(location=1) out vec4 Back;
layout(location=2) out vec4 EmissionDepth;
uniform mat4 InverseVP;
uniform vec3 Eye,Center;
uniform vec2 ScreenSize;
uniform sampler2D OpaqueDepth,SurfaceDepth,Positions;
uniform int Mode,Phase,GlassCount;
uniform float Progress,Time,Seed,Visibility;
float distanceAt(vec2 uv,float z) { if(z>=.9999999) return 1e6; vec4 p=InverseVP*vec4(uv*2.-1.,z*2.-1.,1.); return length(p.xyz/p.w-Eye); }
void main() {
    vec2 uv=gl_FragCoord.xy/ScreenSize; float depth=length(World-Eye);
    float opaque=distanceAt(uv,texture(OpaqueDepth,uv).r),surface=distanceAt(uv,texture(SurfaceDepth,uv).r);
    if(depth>opaque+.035) discard;
    vec3 emission=vec3(0);
    if(Mode==0) {
        float edge=smoothstep(.95,1.,max(abs(Local.x),abs(Local.y)));
        float pressure=Phase==1?pow(.5+.5*sin(Time*1.6-length(World-Center)*.5),5.):.3;
        emission=vec3(.40,.66,1.)*edge*.13*pressure;
        if(Phase==2) for(int rootIndex=0;rootIndex<3;rootIndex++) {
            vec3 root=texelFetch(Positions,ivec2(rootIndex*max(GlassCount-1,0)/3,0),0).xyz+Center;
            vec3 delta=World-root; vec2 plane=vec2(dot(delta,Tangent),dot(delta,Bitangent));
            float radius=length(delta),planar=length(plane);
            float growth=7.*smoothstep(.10,.18,Progress)+15.*smoothstep(.36,.41,Progress)+32.*smoothstep(.62,.73,Progress);
            float front=1.-smoothstep(growth-.5,growth+.5,radius);
            float angle=atan(plane.y,plane.x)+float(rootIndex)*1.91;
            float zigzag=sin(planar*5.2+Seed)*.075+sin(planar*13.7)*.023;
            float primary=abs(sin(angle*4.+zigzag))*planar;
            float forks=abs(sin(angle*9.-planar*.09+zigzag*.6))*planar;
            float branch=exp(-forks/max(fwidth(forks),.006))*smoothstep(1.,3.,planar)*.45;
            float crack=exp(-primary/max(fwidth(primary),.009))+branch;
            float delayed=exp(-pow((radius-growth+1.25)/1.4,2.));
            vec3 tint=mix(vec3(.4,.72,1.),vec3(1.,.93,.75),smoothstep(.5,.79,Progress));
            emission+=tint*crack*front*(.12+.5*smoothstep(.32,.79,Progress)+delayed*.65);
        }
    } else {
        // Each batch quad is trimmed into a different splinter silhouette; no square confetti.
        float edgeA=1.-abs(Local.y),edgeB=(.80-.36*Local.y)-abs(Local.x+Local.y*(ShardSeed-.5)*.42);
        float polygon=min(edgeA,edgeB); if(polygon<0.) discard;
        float rim=1.-smoothstep(.0,.11,polygon);
        vec3 normal=normalize(cross(Tangent,Bitangent));
        float grazing=pow(1.-abs(dot(normal,normalize(Eye-World))),2.);
        vec3 film=.5+.5*cos(vec3(0.,2.3,4.6)+ShardSeed*7.+grazing*3.8);
        vec3 glass=mix(vec3(.5,.7,1.),film,.6)*(.035+grazing*.24)+vec3(.72,.87,1.)*rim*.85;
        vec3 stone=vec3(.36,.19,.08)*(.12+rim*.30);
        emission=Kind==0?glass:stone;
        if(Phase==3) emission*=1.-smoothstep(.65,1.,Progress);
    }
    if(Phase==4) emission*=1.-smoothstep(0.,.3,Progress);
    emission*=Visibility;
    Front=vec4(depth<=surface+.035?emission:vec3(0),0); Back=vec4(depth>surface+.035?emission:vec3(0),0);
    EmissionDepth=vec4(0,0,depth<=surface+.035?depth:65504.,depth>surface+.035?depth:65504.);
}
