package com.mega.overheaven.foliagecompat.worldwarp.client;

/** Tags are acknowledged only after the host accepts and uploads this result. */
public interface WarpBuildOutput {
    void overheaven$sorted(WarpSection section, long epoch);
    void overheaven$uploaded();
}
