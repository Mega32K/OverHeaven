#version 330 core
in vec2 texCoord;
layout(location=0) out vec4 color;
layout(location=1) out float depth;
uniform sampler2D Source;
uniform sampler2D Depth;
uniform sampler2D ExtraDepth;
uniform int MergeDepth;
uniform int Mode;
void main() {
    color = texture(Source, texCoord);
    depth = texture(Depth, texCoord).r;
    if (MergeDepth == 1) depth = min(depth, texture(ExtraDepth,texCoord).r);
    if (Mode == 1) color = vec4(vec3(1.0 - color.a), 1.0);
    if (Mode == 2) color = vec4(1.0 - color.rgb, 1.0);
}
