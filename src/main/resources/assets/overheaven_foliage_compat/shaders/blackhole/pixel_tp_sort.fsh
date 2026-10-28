#version 330 core
// Threshold-delimited horizontal strips, capped at 128 seed pixels before stretching.
// RGBA32F alpha first stores a boundary, then an exact (strip ID, original index) pair.
uniform sampler2D Source;
uniform int SceneWidth;
uniform int Pass;
uniform int Stage;
uniform int Step;
uniform int FirstPass;
uniform float Amount;
layout(location = 0) out vec4 fragColor;

float luminance(vec3 color) {
    return dot(max(color, vec3(0.0)), vec3(0.2126, 0.7152, 0.0722));
}
vec4 sortable(ivec2 p) {
    vec4 value = texelFetch(Source, p, 0);
    if (FirstPass != 0) {
        int index = p.x & 127;
        int boundary = int(value.a);
        // Threshold-excluded pixels are singleton strips and retain their exact position.
        int strip = 2 * boundary - (boundary == index + 1 ? 1 : 0);
        value.a = float(strip * 128 + index);
    }
    return value;
}
void main() {
    ivec2 p = ivec2(gl_FragCoord.xy);
    int index = p.x & 127;
    if (Pass == 0) {
        vec3 color = p.x < SceneWidth ? texelFetch(Source, p, 0).rgb : vec3(0.0);
        float threshold = mix(0.82, 0.025, smoothstep(0.0, 0.72, Amount));
        bool selected = p.x < SceneWidth && luminance(color) >= threshold;
        fragColor = vec4(color, selected ? 0.0 : float(index + 1));
        return;
    }
    if (Pass == 1) {
        vec4 value = texelFetch(Source, p, 0);
        if (index >= Step) value.a = max(value.a, texelFetch(Source, p - ivec2(Step, 0), 0).a);
        fragColor = value;
        return;
    }
    vec4 a = sortable(p), b = sortable(ivec2(p.x ^ Step, p.y));
    int groupA = int(a.a) / 128, groupB = int(b.a) / 128;
    float ka = luminance(a.rgb), kb = luminance(b.rgb);
    bool less = groupA < groupB || (groupA == groupB && (ka < kb || (ka == kb && a.a < b.a)));
    bool ascending = (index & Stage) == 0;
    bool low = (index & Step) == 0;
    fragColor = (ascending == low) ? (less ? a : b) : (less ? b : a);
}
