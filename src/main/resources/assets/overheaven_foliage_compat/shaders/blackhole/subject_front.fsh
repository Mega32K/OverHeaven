#version 330 core
in vec2 texCoord;
layout(location=0) out vec4 Color;
uniform sampler2D CachedSubject;
uniform sampler2D Bloom;
uniform sampler2D Transmission;
uniform int LayerMode;
#include "field.glsl"
#include "singularity_glare.glsl"
#include "composite_light.glsl"
void main() {
    float opaque=distanceAt(texCoord,texture(OpaqueDepth,texCoord).r);
    float surface=distanceAt(texCoord,texture(SurfaceDepth,texCoord).r);
    bool transparent=surface<opaque-max(.002,opaque*.00001);
    vec4 front=texture(CachedSubject,texCoord);
    Color=vec4(finiteHighRadiance(front.rgb+bloomAt(texCoord,opaque)+singularityGlare(texCoord,opaque,surface,transparent)),front.a);
}
