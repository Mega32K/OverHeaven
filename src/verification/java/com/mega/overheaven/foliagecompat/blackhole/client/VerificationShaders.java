package com.mega.overheaven.foliagecompat.blackhole.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

/** Same bounded includes as the production loader, also usable against before-snapshots. */
final class VerificationShaders {
    static String function(Path directory,String signature) throws IOException {
        String source=Files.readString(directory.resolve("field.glsl")).replace("\r\n","\n");
        int start=source.indexOf(signature),brace=source.indexOf('{',start),depth=1,end=brace+1;
        if(start<0 || brace<0) throw new AssertionError(signature);
        while(depth>0 && end<source.length()) { char c=source.charAt(end++);if(c=='{')depth++;else if(c=='}')depth--; }
        return source.substring(start,end);
    }
    static String read(Path directory,String file) throws IOException {
        String result=Files.readString(directory.resolve(file));
        for(String include:new String[]{"field.glsl","finale_remnant.glsl","finale_cosmos.glsl","finale_energy.glsl","singularity_glare.glsl","finale_sky_common.glsl"}) {
            String directive="#include \""+include+"\"";
            if(result.contains(directive)) result=result.replace(directive,read(directory,include));
        }
        return result;
    }
}
