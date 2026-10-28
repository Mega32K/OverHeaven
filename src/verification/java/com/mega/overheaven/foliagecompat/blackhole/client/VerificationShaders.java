package com.mega.overheaven.foliagecompat.blackhole.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

/** Same bounded includes as the production loader, also usable against before-snapshots. */
final class VerificationShaders {
    static String read(Path directory,String file) throws IOException {
        String result=Files.readString(directory.resolve(file));
        for(String include:new String[]{"field.glsl","finale_remnant.glsl","finale_cosmos.glsl","finale_energy.glsl","singularity_glare.glsl","finale_sky_common.glsl"}) {
            String directive="#include \""+include+"\"";
            if(result.contains(directive)) result=result.replace(directive,read(directory,include));
        }
        return result;
    }
}
