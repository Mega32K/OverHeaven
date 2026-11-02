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
uniform float BlockGlitchSeconds;
uniform int HasHand;
uniform int Reveal;
uniform int ForceCovered;
uniform int Complete;
uniform int CoverageOnly;
in vec2 texCoord;

// This pass runs after the host display conversion; do not apply another gamma/exposure curve.
const vec3 COVER = vec3(36.0 / 255.0);
float handAt(vec2 uv) {
    return HasHand != 0 ? texture(HandMask, clamp(uv, PixelSize * 0.5, 1.0 - PixelSize * 0.5)).r : 0.0;
}
float protectedHandAt(vec2 uv) {
    return max(handAt(uv), max(max(handAt(uv + vec2(PixelSize.x, 0.0)),
        handAt(uv - vec2(PixelSize.x, 0.0))), max(handAt(uv + vec2(0.0, PixelSize.y)),
        handAt(uv - vec2(0.0, PixelSize.y)))));
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
float waveDistanceAt(vec2 uv, float depth) {
    if (depth < 1.0) return length(reconstruct(uv, depth) - Eye) / 16.0;
    // Sky has no surface depth: scan from the world horizon towards the zenith.
    vec3 ray = normalize(reconstruct(uv, 0.5) - Eye);
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
// Original implementation informed by the wave/scanline/noise layering in Cyber Punk (7lVSDw).
float glitchRandom(uvec3 seed) {
    uint h = seed.x * 1597334677u ^ seed.y * 3812015801u ^ seed.z * 2798796415u;
    h = (h ^ (h >> 16u)) * 2246822519u;
    h = (h ^ (h >> 13u)) * 3266489917u;
    h ^= h >> 16u;
    return float(h & 16777215u) / 16777216.0;
}

float glitchNoise(vec2 p, uint seed) {
    ivec2 cell = ivec2(floor(p));
    vec2 f = fract(p); f = f*f*(3.0-2.0*f);
    float a = glitchRandom(uvec3(uvec2(cell), seed));
    float b = glitchRandom(uvec3(uvec2(cell + ivec2(1,0)), seed));
    float c = glitchRandom(uvec3(uvec2(cell + ivec2(0,1)), seed));
    float d = glitchRandom(uvec3(uvec2(cell + ivec2(1,1)), seed));
    return mix(mix(a,b,f.x), mix(c,d,f.x), f.y);
}
uint glitchSlice() { return uint(floor(Progress / WaveTiming.x * 10.0)) + uint(Reveal) * 7919u; }
vec2 glitchPixel(vec2 uv) { return uv * vec2(PixelSize.y / PixelSize.x, 1.0) * 1080.0; }
float glitchShape(vec2 uv) {
    vec2 p = glitchPixel(uv);
    uint seed = glitchSlice();
    vec2 warp = vec2(glitchNoise(p / 145.0, seed + 11u), glitchNoise(p / 117.0, seed + 53u)) - 0.5;
    p += warp * 165.0;
    float field = 0.65 * glitchNoise(p / vec2(300.0, 190.0), seed);
    field += 0.25 * glitchNoise(p / vec2(90.0, 55.0), seed + 29u);
    field += 0.10 * glitchNoise(p / vec2(31.0, 19.0), seed + 97u);
    return smoothstep(0.40, 0.50, field);
}
float glitchEnvelope(float age) {
    float localTime = Reveal != 0 ? age - WaveTiming.w : age + BlockGlitchSeconds;
    if (localTime <= 0.0 || localTime >= BlockGlitchSeconds) return 0.0;
    float t = localTime / BlockGlitchSeconds;
    return smoothstep(0.0, 0.2, t) * (1.0 - smoothstep(0.65, 1.0, t));
}
float glitchStrengthAt(vec2 uv, float distance, float depth) {
    // The added glitch/sorting layer only affects surfaces, never the sky depth sentinel.
    if (depth >= 1.0) return 0.0;
    if (abs(distance - Radius) < length(PixelSize) * 24.0 * 4.5) return 0.0;
    float envelope = glitchEnvelope(Progress / WaveTiming.x - scanTimestamp(distance));
    return envelope > 0.0 ? envelope * glitchShape(uv) : 0.0;
}
// Partition a short final row span into powers of two without padding or losing texels.
// All full spans contain 128 native-resolution pixels; this never downsamples Scene.
ivec2 sortSegment(int x, int width) {
    int base = (x / 128) * 128;
    for (int span = 128; span >= 1; span >>= 1) {
        if (base + span <= width) {
            if (x < base + span) return ivec2(base, span);
            base += span;
        }
    }
    return ivec2(x, 1);
}
// XsBfRG's horizontal, bright-run ordering, implemented with an independent stable sorting network.
// Keep the float key and original RGB; there is no histogram binning or RGB index packing.
float sortLuminance(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }
// Display-space scenes can be dark: retain threshold boundaries without excluding most terrain.
float sortThreshold() { return 0.04 + 0.14 * (0.5 + 0.5 * sin(Progress / WaveTiming.x * 5.0)); }
vec3 blockSceneAt(vec2 uv, vec3 fallback) {
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThanEqual(uv, vec2(1.0)))) return fallback;
    // Scene/depth copies use nearest filtering; test precisely the texel that will supply color.
    uv = (floor(uv / PixelSize) + 0.5) * PixelSize;
    if (protectedHandAt(uv) > 0.0) return fallback;
    float depth = texture(SceneDepth, uv).r;
    if (depth >= 1.0) return fallback;
    float sourceAge = Progress / WaveTiming.x - scanTimestamp(waveDistanceAt(uv, depth));
    bool sourceVisible = Reveal != 0 ? sourceAge >= WaveTiming.w : sourceAge < 0.0;
    return sourceVisible ? texture(Scene, uv).rgb : fallback;
}
