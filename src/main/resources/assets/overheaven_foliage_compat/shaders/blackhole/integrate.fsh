#version 330 core
in vec2 texCoord;
layout(location=0) out vec4 Front;
layout(location=1) out vec4 Back;
layout(location=2) out vec4 Lens;
layout(location=3) out vec4 Meta;
#include "field.glsl"
void main() { Field result = integrate(texCoord); Front=result.front; Back=result.back; Lens=result.lens; Meta=result.meta; }
