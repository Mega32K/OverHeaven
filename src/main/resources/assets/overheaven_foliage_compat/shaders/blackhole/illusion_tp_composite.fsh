#version 330 core
uniform sampler2D Scene;
uniform sampler2D SceneDepth;
uniform sampler2D HandMask;
uniform mat4 InverseVP;
uniform vec3 Eye;
uniform vec2 PixelSize;
uniform float Horizon;
uniform float Radius;
uniform float Progress;
// Time scale, initial rate, ramp duration (seconds), local depth fade duration (seconds).
uniform vec4 WaveTiming;
uniform int HasHand;
uniform int Reveal;
uniform int ForceCovered;
uniform int Complete;
uniform int CoverageOnly;
in vec2 texCoord;
out vec4 fragColor;

// This pass runs after the host display conversion; do not apply another gamma/exposure curve.
const vec3 COVER = vec3(36.0 / 255.0);
float handAt(vec2 uv) {
    return HasHand != 0 ? texture(HandMask, clamp(uv, PixelSize * 0.5, 1.0 - PixelSize * 0.5)).r : 0.0;
}
vec3 sceneAt(vec2 uv, vec3 fallback) {
    uv = clamp(uv, PixelSize * 0.5, 1.0 - PixelSize * 0.5);
    // A displaced world sample must not pull the protected hand into the scan band.
    return handAt(uv) > 0.0 ? fallback : texture(Scene, uv, -10.0).rgb;
}
vec3 reconstruct(vec2 uv, float depth) {
    vec4 position = InverseVP * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return position.xyz / position.w;
}
// Display-space greys: geometry distance controls brightness, not the scene texture or raw depth.
vec3 depthCover(float depth) {
    if (depth >= 1.0) return COVER;
    float blocks = length(reconstruct(texCoord, depth) - Eye);
    float strength = exp2(-blocks / 32.0);
    return mix(COVER, vec3(112.0 / 255.0), strength);
}
float waveDistance(float depth) {
    if (depth < 1.0) return length(reconstruct(texCoord, depth) - Eye) / 16.0;
    // Sky has no surface depth: scan from the world horizon towards the zenith.
    vec3 ray = normalize(reconstruct(texCoord, 0.5) - Eye);
    float elevation = asin(clamp(ray.y, 0.0, 1.0)) / 1.57079632679;
    return Horizon * (1.0 + elevation);
}
// Invert the exact CPU ramp to obtain this surface's scan timestamp.
// Once the ramp ends the inverse is analytic; only early hits need bounded bisection.
float scanTimestamp(float distance) {
    float target = sqrt(max(distance, 0.0)) / WaveTiming.x;
    float initial = WaveTiming.y;
    float ramp = WaveTiming.z;
    float rampEnd = ramp * (initial + (1.0 - initial) * 0.5);
    if (target >= rampEnd) return target + ramp * (1.0 - initial) * 0.5;
    float lo = 0.0, hi = ramp;
    for (int i = 0; i < 14; ++i) {
        float mid = (lo + hi) * 0.5;
        float u = mid / ramp;
        float accumulated = initial * mid + (1.0 - initial) * ramp * (u*u*u - 0.5*u*u*u*u);
        if (accumulated < target) lo = mid; else hi = mid;
    }
    return (lo + hi) * 0.5;
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
    float hand = max(handAt(texCoord), max(max(handAt(texCoord + vec2(PixelSize.x, 0.0)),
        handAt(texCoord - vec2(PixelSize.x, 0.0))), max(handAt(texCoord + vec2(0.0, PixelSize.y)),
        handAt(texCoord - vec2(0.0, PixelSize.y)))));
    if (hand > 0.0) {
        if (CoverageOnly != 0) discard;
        fragColor = original; return;
    }
    if (ForceCovered != 0) { fragColor = vec4(COVER, 1.0); return; }
    // Completion is latched only after the wave AND its fading tail have cleared the view.
    if (Complete != 0) { fragColor = Reveal != 0 ? original : vec4(COVER, 1.0); return; }
    float depth = texture(SceneDepth, texCoord).r;
    float distance = waveDistance(depth);
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
        // A covered surface is not ready for teleport while its depth silhouette is still fading.
        bool depthFaded = age >= WaveTiming.w;
        if (offset <= -width * widthMultiplier && depthFaded) discard;
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
    }
    fragColor = vec4(color, 1.0);
}
