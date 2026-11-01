#version 330 core
in vec2 texCoord;
out vec4 Color;
uniform sampler2D FrontTexture,BackTexture,MetaTexture;
uniform int FallbackOnly;
#include "field.glsl"
#include "reconstruct.glsl"
void main() {
    vec3 ray=rayAt(texCoord);
    float opaque=distanceAt(texCoord,texture(OpaqueDepth,texCoord).r);
    vec2 bounds=sphere(Eye-Center,ray,max(bodyExtent(),FinaleAmount>0.?max(max(32.,Radius*1.5),WaveSupport):0.));
    if(bounds.y<=0. || max(0.,bounds.x)>=opaque) discard;
    if(FallbackOnly!=0) {
        if(relativistic() && all(equal(textureSize(FrontTexture,0),ivec2(SceneSize))) && solutionContains(ivec2(gl_FragCoord.xy))) discard;
        float surface=distanceAt(texCoord,texture(SurfaceDepth,texCoord).r);
        if(surface>=opaque-max(.002,opaque*.00001)) surface=opaque;
        vec2 core=sphere(Eye-Center,ray,max(coreRadius(),.000001));
        bool captured=core.y>0. && max(0.,core.x)<opaque;
        Field f;float total;reconstructRadiance(texCoord,opaque,surface,captured,f,total);
        if(total>=.01) discard;
    }
    Color=vec4(0);
}
