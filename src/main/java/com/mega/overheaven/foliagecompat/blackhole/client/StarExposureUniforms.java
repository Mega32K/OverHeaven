package com.mega.overheaven.foliagecompat.blackhole.client;

import com.mega.overheaven.foliagecompat.finale.FinaleStarExposure;

final class StarExposureUniforms {
    private StarExposureUniforms() { }
    static void apply(GlProgram p,FinaleStarExposure exposure) {
        p.integer("ExposureEnabled",exposure.enabled?1:0);
        p.scalar("ExposureSeconds",exposure.seconds);
        p.floats("ExposureAngles[0]",exposure.full);
        p.floats("ExposureTravel[0]",exposure.travel);
        p.floats("GalaxyAngles[0]",exposure.galaxy);
        p.integer("GalaxySamples",exposure.galaxySamples);
    }
}
