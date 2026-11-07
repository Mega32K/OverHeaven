// Preserve the host's inout contract; geometry callers keep their source-space normal.
vec4 GetReflection(inout vec3 normalM, vec3 viewPos, vec3 nViewPos, vec3 playerPos, float lViewPos, float z0,
                   sampler2D depthtex, float dither, float skyLightFactor, float fresnel,
                   float smoothness, vec3 geoNormal, vec3 color, vec3 shadowMult, float highlightMult) {
    #if defined GBUFFERS_WATER
        if (ohWarpSlope != 0.0 && ohWarpStage(renderStage)) {
            vec3 visualNormal = mat3(gbufferModelView) * ohWarpNormal(playerPos, mat3(gbufferModelViewInverse) * normalM);
            vec3 visualGeo = mat3(gbufferModelView) * ohWarpNormal(playerPos, mat3(gbufferModelViewInverse) * geoNormal);
            vec4 result = ohWarpGetReflection(visualNormal, viewPos, nViewPos, playerPos, lViewPos, z0,
                depthtex, dither, skyLightFactor, fresnel, smoothness, visualGeo, color, shadowMult, highlightMult);
            normalM = mat3(gbufferModelView) * ohUnwarpNormal(playerPos, mat3(gbufferModelViewInverse) * visualNormal);
            return result;
        }
    #endif
    return ohWarpGetReflection(normalM, viewPos, nViewPos, playerPos, lViewPos, z0,
        depthtex, dither, skyLightFactor, fresnel, smoothness, geoNormal, color, shadowMult, highlightMult);
}
