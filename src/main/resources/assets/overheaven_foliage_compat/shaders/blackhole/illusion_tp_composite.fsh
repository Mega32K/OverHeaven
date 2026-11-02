#version 330 core
#include "illusion_tp_common.glsl"
uniform sampler2D SortedIndices;
out vec4 fragColor;

vec3 sortedSceneAt(vec2 uv, vec3 fallback) {
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThanEqual(uv, vec2(1.0)))) return fallback;
    ivec2 p = ivec2(floor(uv / PixelSize));
    vec2 center = (vec2(p) + 0.5) * PixelSize;
    float depth = texelFetch(SceneDepth, p, 0).r;
    float distance = waveDistanceAt(center, depth);
    if (protectedHandAt(center) > 0.0 || glitchStrengthAt(center, distance, depth) <= 0.0) return fallback;
    int record = int(texelFetch(SortedIndices, p, 0).r);
    int strip = record / 128;
    if ((strip & 1) != 0) return blockSceneAt(center, fallback);
    // Each ordered pixel retains exactly one source RGB, rather than stretching or repeating a color.
    int originalX = (p.x / 128) * 128 + (record & 127);
    return blockSceneAt((vec2(originalX, p.y) + 0.5) * PixelSize, fallback);
}
vec3 glitchSceneAt(vec2 uv, vec3 fallback) {
    vec3 displaced = blockSceneAt(uv, fallback);
    // Sort first, then tear/split this image. An excluded sorting pixel still keeps its displacement.
    return mix(displaced, sortedSceneAt(uv, displaced), 0.70);
}
vec3 blockGlitch(float age, vec3 original) {
    float envelope = glitchEnvelope(age);
    if (envelope <= 0.0) return original;
    float strength = envelope * glitchShape(texCoord);
    if (strength <= 0.0) return original;
    float impact = smoothstep(0.0, 0.65, strength);
    vec2 p = glitchPixel(texCoord);
    uint seed = glitchSlice();
    float time = Progress / WaveTiming.x;
    // Broad scanline tears and coarse jumps remain visible even when the noise waves cross zero.
    float wave = (glitchNoise(vec2(p.y / 63.0, time * 12.0), 101u) * 2.0 - 1.0)
        * (glitchNoise(vec2(p.y / 29.0, time * 21.0), 211u) * 2.0 - 1.0) * 260.0;
    float row = glitchRandom(uvec3(uint(p.y / 12.0), 47u, seed));
    float tear = abs(row - 0.5) > 0.18 ? (row - 0.5) * 480.0 : 0.0;
    float jumpNoise = glitchNoise(p / vec2(230.0, 150.0), seed + 61u);
    float jump = (row < 0.5 ? -1.0 : 1.0) * (45.0 + 110.0 * jumpNoise);
    vec2 unit = vec2(PixelSize.x / PixelSize.y, 1.0) / 1080.0;
    vec2 displaced = texCoord + vec2(wave + tear + jump, (row - 0.5) * 36.0) * unit * impact;
    vec2 split = vec2(20.0 + 44.0 * abs(row - 0.5) * 2.0, (row - 0.5) * 14.0) * unit * impact;
    vec3 localSorted = sortedSceneAt(texCoord, original);
    vec3 color = vec3(glitchSceneAt(displaced + split, localSorted).r,
        glitchSceneAt(displaced, localSorted).g, glitchSceneAt(displaced - split, localSorted).b);
    uvec3 grainSeed = uvec3(uvec2(p), uint(time * 30.0) + uint(Reveal) * 7919u);
    vec3 grain = vec3(glitchRandom(grainSeed), glitchRandom(grainSeed + uvec3(17u,3u,5u)),
        glitchRandom(grainSeed + uvec3(7u,19u,11u))) - 0.5;
    color += grain * 0.025 * impact;
    return mix(original, max(color, vec3(0.0)), impact);
}
// DepthTestPostEffect reference: retain its strip displacement, timing and 20-sample spectrum.
float sat(float t) { return clamp(t, 0.0, 1.0); }
float remap(float t, float a, float b) { return sat((t - a) / (b - a)); }
float linterp(float t) { return sat(1.0 - abs(2.0 * t - 1.0)); }
vec3 spectrumOffset(float t) {
    float lo = step(t, 0.5);
    float w = linterp(remap(t, 1.0 / 6.0, 5.0 / 6.0));
    vec3 weights = vec3(lo, 1.0, 1.0 - lo) * vec3(1.0 - w, w, 1.0 - w);
    return pow(weights, vec3(1.0 / 2.2));
}
float rand(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
float mytrunc(float x, float levels) {
    // The reference can reach zero levels at a quantized time; keep that cell finite.
    return levels > 0.0 ? floor(x * levels) / levels : 0.0;
}
vec3 glitch(float strength, bool greySample, vec3 fallback) {
    vec2 uv = texCoord;
    float time = mod(Progress * 100.0, 32.0) / 110.0;
    float gnm = sat(strength);
    float rnd0 = rand(vec2(mytrunc(time, 6.0)));
    float r0 = sat((1.0 - gnm) * 0.7 + rnd0);
    float rnd1 = rand(vec2(mytrunc(uv.x, 10.0 * r0), time));
    float r1 = 0.5 - 0.5 * gnm + rnd1;
    r1 = 1.0 - max(0.0, r1 < 1.0 ? r1 : 0.9999999);
    float r2 = sat(rand(vec2(mytrunc(uv.y, 40.0 * r1), time)));
    float rnd3 = rand(vec2(mytrunc(uv.y, 10.0 * r0), time));
    float r3 = (1.0 - sat(rnd3 + 0.8)) - 0.1;
    float pxrnd = rand(uv + time);
    float ofs = 0.05 * r2 * strength * (rnd0 > 0.5 ? 1.0 : -1.0);
    ofs += 0.5 * pxrnd * ofs;
    uv.y += 0.1 * r3 * strength;

    vec3 sum = vec3(0.0);
    vec3 wsum = vec3(0.0);
    const int NUM_SAMPLES = 20;
    for (int i = 0; i < NUM_SAMPLES; ++i) {
        float t = float(i) / float(NUM_SAMPLES);
        uv.x = sat(uv.x + ofs * t);
        vec3 sampleColor = sceneAt(uv, fallback);
        if (greySample) sampleColor = vec3(sat(dot(sampleColor, vec3(0.299, 0.587, 0.114))));
        vec3 weights = spectrumOffset(t);
        sum += sampleColor * weights;
        wsum += weights;
    }
    return sum / wsum;
}
void main() {
    vec4 original = texture(Scene, texCoord);
    // Keep a one-pixel fringe for the hand's antialiased silhouette.
    float hand = protectedHandAt(texCoord);
    if (hand > 0.0) {
        if (CoverageOnly != 0) discard;
        fragColor = original; return;
    }
    if (ForceCovered != 0) { fragColor = vec4(COVER, 1.0); return; }
    // Completion is latched only after the wave AND its fading tail have cleared the view.
    if (Complete != 0) { fragColor = Reveal != 0 ? original : vec4(COVER, 1.0); return; }
    float depth = texture(SceneDepth, texCoord).r;
    float distance = waveDistanceAt(texCoord, depth);
    // The local deadline is hitTime + duration, never a whole-screen phase deadline.
    float hitTime = scanTimestamp(distance);
    float elapsed = Progress / WaveTiming.x;
    float age = elapsed - hitTime;
    float fade = smoothstep(0.0, WaveTiming.w, age);
    vec3 depthColor = depthCover(depth);
    vec3 covered = Reveal != 0 ? mix(COVER, depthColor, fade) : mix(depthColor, COVER, fade);
    float offset = distance - Radius;
    // Keep the reference width and falloff exactly; no minimum width or derivative expansion.
    float width = length(PixelSize) * 24.0;
    const float widthMultiplier = 4.5;
    if (CoverageOnly != 0) {
        // Wait for the whole local tail, even if this random slice contains no active block.
        float settledAge = WaveTiming.w + (Reveal != 0 ? BlockGlitchSeconds : 0.0);
        if (offset <= -width * widthMultiplier && age >= settledAge) discard;
        fragColor = vec4(0.0); return;
    }
    // In reveal the bright/glitch edge is delayed until the local depth fade-in has finished.
    bool visible = Reveal != 0 ? age >= WaveTiming.w : age < 0.0;
    vec3 color = visible ? original.rgb : covered;
    float distanceToProgress = abs(offset);
    if (distanceToProgress < width * widthMultiplier) {
        if (distanceToProgress <= width) {
            color += vec3(1.0 - smoothstep(0.0, 1.0, distanceToProgress / width));
        } else {
            // Reference strength is based on camera distance, not the much smaller band offset.
            float strength = (distance - width) / width / (widthMultiplier - 1.0) * 0.4;
            color = glitch(strength, !visible, original.rgb);
        }
    } else if (visible && depth < 1.0) {
        color = blockGlitch(age, original.rgb);
    }
    fragColor = vec4(color, 1.0);
}
