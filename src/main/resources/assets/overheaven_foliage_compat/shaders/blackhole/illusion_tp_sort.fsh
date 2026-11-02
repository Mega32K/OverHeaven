#version 330 core
#include "illusion_tp_common.glsl"
uniform sampler2D Source;
uniform int Pass;
uniform int Step;
uniform int Stage;
uniform int FirstPass;
layout(location = 0) out float sortRecord;

// R32F stores exact small integer indices, never quantized RGB/brightness bins.
int recordAt(ivec2 p) {
    int value = int(texelFetch(Source, p, 0).r);
    if (FirstPass != 0) {
        int index = p.x & 127;
        int strip = 2 * value - (value == index + 1 ? 1 : 0);
        return strip * 128 + index;
    }
    return value;
}
void main() {
    ivec2 p = ivec2(gl_FragCoord.xy);
    ivec2 segment = sortSegment(p.x, textureSize(Scene, 0).x);
    int base = (p.x / 128) * 128;
    if (Pass == 0) {
        vec2 uv = (vec2(p) + 0.5) * PixelSize;
        float depth = texelFetch(SceneDepth, p, 0).r;
        float distance = waveDistanceAt(uv, depth);
        bool selected = protectedHandAt(uv) == 0.0 && glitchStrengthAt(uv, distance, depth) > 0.0
            && sortLuminance(texelFetch(Scene, p, 0).rgb) >= sortThreshold();
        sortRecord = float(selected ? segment.x - base : (p.x & 127) + 1);
        return;
    }
    if (Pass == 1) {
        float value = texelFetch(Source, p, 0).r;
        if (p.x - segment.x >= Step) value = max(value, texelFetch(Source, p - ivec2(Step,0), 0).r);
        sortRecord = value;
        return;
    }
    int a = recordAt(p);
    if (Stage > segment.y) { sortRecord = float(a); return; }
    int b = recordAt(ivec2(p.x ^ Step, p.y));
    bool less = a / 128 < b / 128;
    if (a / 128 == b / 128) {
        float keyA = sortLuminance(texelFetch(Scene, ivec2(base + (a & 127), p.y), 0).rgb);
        float keyB = sortLuminance(texelFetch(Scene, ivec2(base + (b & 127), p.y), 0).rgb);
        less = keyA < keyB || (keyA == keyB && a < b);
    }
    bool ascending = ((p.x - segment.x) & Stage) == 0;
    bool low = ((p.x - segment.x) & Step) == 0;
    sortRecord = float(ascending == low ? (less ? a : b) : (less ? b : a));
}
