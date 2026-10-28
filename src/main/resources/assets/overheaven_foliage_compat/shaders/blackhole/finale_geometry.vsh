#version 330 core
uniform mat4 VP;
uniform vec3 Center;
uniform sampler2D Positions,Shapes;
uniform int Mode,Phase,GlassCount,GlassFragments;
uniform float Progress,Time,Seed;
out vec3 World;
out vec2 Local;
flat out int Kind;
flat out vec3 Tangent,Bitangent;
flat out float ShardSeed;
float hash(float value) { return fract(sin(value*127.1+Seed)*43758.5453); }
vec3 rotate(vec3 point,vec3 axis,float angle) { return point*cos(angle)+cross(axis,point)*sin(angle)+axis*dot(axis,point)*(1.-cos(angle)); }
void main() {
    vec2 corners[6]=vec2[](vec2(-1,-1),vec2(1,-1),vec2(1,1),vec2(-1,-1),vec2(1,1),vec2(-1,1));
    Local=corners[gl_VertexID%6]; int index=gl_InstanceID;
    if(Mode==1) index=gl_InstanceID<GlassFragments?min(GlassCount-1,int(float(gl_InstanceID)*float(GlassCount)/float(max(GlassFragments,1)))):GlassCount+gl_InstanceID-GlassFragments;
    vec4 item=Mode==0?texelFetch(Shapes,ivec2(0,index),0):texelFetch(Positions,ivec2(index,0),0); Kind=int(item.w);
    vec3 position=item.xyz,vertex; ShardSeed=hash(float(gl_InstanceID));
    if(Mode==0) {
        int face=gl_VertexID/6; vec3 normal=face<2?vec3(1,0,0):face<4?vec3(0,1,0):vec3(0,0,1); if(face%2==1) normal=-normal;
        Tangent=face<2?vec3(0,0,1):vec3(1,0,0); Bitangent=cross(normal,Tangent);
        vec3 halfSize=texelFetch(Shapes,ivec2(1,index),0).xyz;
        vertex=position+normal*(dot(abs(normal),halfSize)+.002)+Tangent*Local.x*dot(abs(Tangent),halfSize)+Bitangent*Local.y*dot(abs(Bitangent),halfSize);
    } else {
        float h=ShardSeed,burst=Phase==2?clamp((Progress-.8)/.2,0.,1.):1.;
        // Fast initial impulse, visible braking, then a distance-delayed turn back toward the source.
        float outward=1.-pow(1.-burst,3.);
        float delay=clamp(length(position)/24.,0.,1.)*.18;
        float inward=Phase>=4?1.:Phase==3?smoothstep(.12+delay,.96,Progress):0.;
        vec3 radial=normalize(position+vec3(.0001)),axis=normalize(vec3(.24,.91,-.34));
        vec3 tangent=normalize(cross(radial,axis)+vec3(.0001));
        vec3 launch=position+radial*(2.+h*5.)*outward+tangent*(h-.5)*outward*1.4;
        float distance=length(launch)*(1.-pow(inward,.78));
        vec3 orbit=rotate(normalize(launch),axis,inward*inward*(1.7+h*2.2));
        position=orbit*distance;
        float angle=(Time*(.55+h)+h*6.283)*(1.-inward*.35);
        Tangent=normalize(rotate(tangent,radial,angle)); Bitangent=normalize(cross(radial,Tangent));
        float size=(.075+h*.24)*(1.-smoothstep(.65,1.,inward))*(Kind==0?1.:1.5);
        vertex=position+(Tangent*Local.x*(.55+h*.5)+Bitangent*Local.y*(1.2+h))*size;
    }
    World=Center+vertex; gl_Position=VP*vec4(World,1);
}
