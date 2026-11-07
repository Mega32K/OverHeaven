#ifndef OH_COLOR_LOSS_COMMON
#define OH_COLOR_LOSS_COMMON
// Classification only; output stays in the caller's working/display domain.
// Oklab matrices: Bjorn Ottosson, https://bottosson.github.io/posts/oklab/ (MIT).
vec3 ohSrgbToLinear(vec3 c) {
    c = max(c, vec3(0.0));
    return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), c));
}
vec3 ohOklab(vec3 c) {
    vec3 lms = vec3(dot(c, vec3(0.4122214708, 0.5363325363, 0.0514459929)),
                    dot(c, vec3(0.2119034982, 0.6806995451, 0.1073969566)),
                    dot(c, vec3(0.0883024619, 0.2817188376, 0.6299787005)));
    lms = pow(max(lms, vec3(0.0)), vec3(1.0 / 3.0));
    return vec3(dot(lms, vec3(0.2104542553, 0.7936177850, -0.0040720468)),
                dot(lms, vec3(1.9779984951, -2.4285922050, 0.4505937099)),
                dot(lms, vec3(0.0259040371, 0.7827717662, -0.8086757660)));
}
float ohHueLoss(float h, vec3 rgb) {
    // Mixed hues fade with the second lost primary, leaving only the remaining primary family.
    float mixed = max(min(rgb.r, rgb.g), max(min(rgb.g, rgb.b), min(rgb.b, rgb.r)));
    if (h < 40.0) return rgb.r;
    if (h < 52.0) return mix(rgb.r, mixed, smoothstep(40.0, 52.0, h));
    if (h < 104.0) return mixed;
    if (h < 118.0) return mix(mixed, rgb.g, smoothstep(104.0, 118.0, h));
    if (h < 168.0) return rgb.g;
    if (h < 190.0) return mix(rgb.g, rgb.b, smoothstep(168.0, 190.0, h));
    // Cyan belongs to the Blue control, including cyan wool and cyan lighting.
    if (h < 288.0) return rgb.b;
    if (h < 302.0) return mix(rgb.b, mixed, smoothstep(288.0, 302.0, h));
    if (h < 338.0) return mixed;
    if (h < 350.0) return mix(mixed, rgb.r, smoothstep(338.0, 350.0, h));
    return rgb.r;
}
float ohSelectiveLoss(vec3 linearSource, vec3 rgb) {
    vec3 lab = ohOklab(linearSource);
    float relativeChroma = length(lab.yz) / max(lab.x, 0.0000001);
    if (relativeChroma <= 0.02) return 0.0;
    float hue = mod(degrees(atan(lab.z, lab.y)) + 360.0, 360.0);
    return ohHueLoss(hue, rgb) * smoothstep(0.02, 0.06, relativeChroma);
}
vec3 ohDesaturate(vec3 source, float loss, vec3 luma) {
    return mix(source, vec3(dot(source, luma)), loss);
}
#ifdef OH_COLOR_LOSS_PACK
uniform vec3 ohColorLossResidual;
uniform bool ohColorLossResetHistory;
vec3 ohLossSrgb(vec3 source) {
    if (all(equal(ohColorLossResidual, vec3(0.0)))) return source;
    return ohDesaturate(source, ohSelectiveLoss(ohSrgbToLinear(source), ohColorLossResidual), vec3(0.299, 0.587, 0.114));
}
vec3 ohLossLinear(vec3 source) {
    if (all(equal(ohColorLossResidual, vec3(0.0)))) return source;
    return ohDesaturate(source, ohSelectiveLoss(max(source, vec3(0.0)), ohColorLossResidual), vec3(0.2126, 0.7152, 0.0722));
}
vec3 ohLitMaterial(vec3 albedo, vec3 diffuse, vec3 highlight) {
    vec3 original = albedo * diffuse + highlight;
    if (all(equal(ohColorLossResidual, vec3(0.0)))) return original;
    vec3 changed = ohLossSrgb(albedo) * ohLossSrgb(diffuse) + ohLossSrgb(highlight);
    float before = dot(original, vec3(0.299, 0.587, 0.114));
    float after = dot(changed, vec3(0.299, 0.587, 0.114));
    return after > 0.0000001 ? changed * (before / after) : changed;
}
#endif
#endif
