package com.mega.overheaven.foliagecompat.worldwarp.client;

public interface WarpSection {
    int getOriginX();
    int getOriginY();
    int getOriginZ();
    boolean overheaven$invalidateWarpSort(WorldWarpFrame frame);
    void overheaven$warpSorted(long epoch);
}
