#version 330 core
uniform sampler2D Scene;
uniform sampler2D Sorted;
uniform float Amount;
layout(location = 0) out vec4 fragColor;

float rowNoise(float row) {
    return fract(sin(row * 12.9898 + 7.31) * 43758.5453);
}
int stripAt(int x, int y) {
    return int(texelFetch(Sorted, ivec2(x, y), 0).a) / 128;
}
// Sorted strips retain their threshold boundaries. Binary search finds the strip's end.
int stripEnd(int start, int limit, int y, int strip) {
    int lo = start, hi = limit;
    for (int i = 0; i < 8; i++) {
        if (lo >= hi) break;
        int mid = (lo + hi) / 2;
        if (stripAt(mid, y) <= strip) lo = mid + 1;
        else hi = mid;
    }
    return lo;
}
void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    ivec2 size = textureSize(Scene, 0);
    vec4 original = texelFetch(Scene, pixel, 0);
    if (Amount <= 0.0) { fragColor = original; return; }
    if (Amount >= 1.0) { fragColor = vec4(0.0, 0.0, 0.0, original.a); return; }

    float noise = rowNoise(floor(float(pixel.y) / 2.0));
    float growth = clamp((Amount - noise * 0.08) / 0.92, 0.0, 1.0);
    float stretch = 1.0 + (4.0 + 12.0 * noise) * growth * growth;
    float maxReach = min(float(size.x), 128.0 * stretch);
    vec3 streak = texelFetch(Sorted, pixel, 0).rgb;
    int previousStrip = -1;
    // A bounded search for foreground strips extending into this pixel; the nearest wins.
    for (int i = 0; i < 14; i++) {
        float offset = float(i) / 13.0;
        int probe = pixel.x - int(maxReach * offset * offset);
        if (probe < 0) break;
        int blockStart = (probe / 128) * 128;
        int strip = stripAt(probe, pixel.y);
        int identity = blockStart * 256 + strip;
        if (identity == previousStrip) continue;
        previousStrip = identity;
        if ((strip & 1) != 0) continue;
        int start = blockStart + strip / 2;
        int end = stripEnd(start, min(blockStart + 128, size.x), pixel.y, strip);
        if (end - start < 2) continue;
        float sourceX = float(start) + float(pixel.x - start) / stretch;
        if (sourceX < float(start) || sourceX >= float(end)) continue;
        streak = texelFetch(Sorted, ivec2(int(sourceX), pixel.y), 0).rgb;
        break;
    }
    float x = (float(pixel.x) + 0.5) / float(size.x);
    float sweep = smoothstep(-0.12, 0.05, growth * 1.20 - x);
    float sortMix = smoothstep(0.0, 0.18, growth) * sweep;
    vec3 color = mix(original.rgb, streak, sortMix);
    // The trailing edge wipes the sorted image to black, covering the actual teleport.
    float coverPosition = (growth - 0.62) / 0.38 * 1.15;
    float cover = smoothstep(-0.025, 0.025, coverPosition - x);
    color *= 1.0 - cover;
    // The host already applied its display transform; preserve its color and alpha conventions.
    fragColor = vec4(color, original.a);
}
