#ifndef OH_WORLD_WARP
#define OH_WORLD_WARP
uniform vec3 ohWarpCenter;
uniform vec3 ohWarpAxis;
uniform float ohWarpSlope;
uniform float ohWarpDistanceOffset;
uniform vec3 ohWarpPreviousCenter;
uniform vec3 ohWarpPreviousAxis;
uniform float ohWarpPreviousSlope;
uniform float ohWarpPreviousDistanceOffset;
uniform vec3 ohWarpCameraDelta;
uniform bool ohWarpResetHistory;

vec3 ohWarpRotate(vec3 v, vec3 axis, float angle) {
    float c = cos(angle), s = sin(angle);
    return v * c + cross(axis, v) * s + axis * dot(axis, v) * (1.0 - c);
}
vec3 ohWarpAt(vec3 p, vec3 center, vec3 axis, float slope, float distanceOffset) {
    if (slope == 0.0) return p;
    return center + ohWarpRotate(p - center, axis, slope * (dot(p, axis) - distanceOffset));
}
vec3 ohWarp(vec3 p) { return ohWarpAt(p, ohWarpCenter, ohWarpAxis, ohWarpSlope, ohWarpDistanceOffset); }
vec3 ohUnwarp(vec3 p) { return ohWarpAt(p, ohWarpCenter, ohWarpAxis, -ohWarpSlope, ohWarpDistanceOffset); }
vec3 ohWarpPrevious(vec3 p) {
    return ohWarpAt(p, ohWarpPreviousCenter, ohWarpPreviousAxis, ohWarpPreviousSlope, ohWarpPreviousDistanceOffset);
}
vec3 ohWarpHistory(vec3 displayPosition) {
    return ohWarpPrevious(ohUnwarp(displayPosition) + ohWarpCameraDelta);
}
// The inverse-transpose Jacobian includes shear along the twist axis.
vec3 ohWarpNormal(vec3 originalPosition, vec3 n) {
    if (ohWarpSlope == 0.0) return n;
    vec3 q = originalPosition - ohWarpCenter;
    n -= ohWarpAxis * (ohWarpSlope * dot(cross(ohWarpAxis, q), n));
    return normalize(ohWarpRotate(n, ohWarpAxis, ohWarpSlope * (dot(originalPosition, ohWarpAxis) - ohWarpDistanceOffset)));
}
vec3 ohUnwarpDirection(vec3 originalPosition, vec3 direction) {
    vec3 d = ohWarpRotate(direction, ohWarpAxis, -ohWarpSlope * (dot(originalPosition, ohWarpAxis) - ohWarpDistanceOffset));
    return normalize(d - ohWarpSlope * cross(ohWarpAxis, originalPosition - ohWarpCenter) * dot(ohWarpAxis, d));
}
vec3 ohUnwarpNormal(vec3 originalPosition, vec3 n) {
    if (ohWarpSlope == 0.0) return n;
    n = ohWarpRotate(n, ohWarpAxis, -ohWarpSlope * (dot(originalPosition, ohWarpAxis) - ohWarpDistanceOffset));
    return normalize(n + ohWarpAxis * (ohWarpSlope * dot(cross(ohWarpAxis, originalPosition - ohWarpCenter), n)));
}
vec3 ohWarpHistoryNormal(vec3 originalPosition, vec3 previousNormal) {
    if (ohWarpSlope == 0.0 && ohWarpPreviousSlope == 0.0) return previousNormal;
    vec3 previousSource = originalPosition + ohWarpCameraDelta;
    vec3 n = ohWarpRotate(previousNormal, ohWarpPreviousAxis,
                          -ohWarpPreviousSlope * (dot(previousSource, ohWarpPreviousAxis) - ohWarpPreviousDistanceOffset));
    n += ohWarpPreviousAxis * (ohWarpPreviousSlope * dot(cross(ohWarpPreviousAxis, previousSource - ohWarpPreviousCenter), n));
    return ohWarpNormal(originalPosition, normalize(n));
}
bool ohWarpStage(int stage) {
    return stage != MC_RENDER_STAGE_HAND_SOLID && stage != MC_RENDER_STAGE_HAND_TRANSLUCENT;
}
vec3 ohWarpSource(vec3 position, int stage) { return ohWarpStage(stage) ? ohUnwarp(position) : position; }
vec3 ohWarpSurfaceNormal(vec3 p, vec3 n, int stage) { return ohWarpStage(stage) ? ohWarpNormal(p, n) : n; }
vec4 ohWarpView(vec4 viewPosition, mat4 invView, mat4 view, int stage) {
    if (ohWarpSlope == 0.0 || !ohWarpStage(stage)) return viewPosition;
    vec4 p = invView * viewPosition;
    return view * vec4(ohWarp(p.xyz / p.w), 1.0);
}
vec4 ohWarpClip(vec4 clip, mat4 invProjection, mat4 invView, mat4 projection, mat4 view, int stage) {
    if (ohWarpSlope == 0.0 || !ohWarpStage(stage)) return clip;
    vec4 p = invView * invProjection * clip;
    return projection * view * vec4(ohWarp(p.xyz / p.w), 1.0);
}
#endif
