package com.mega.overheaven.foliagecompat.worldwarp.client;

/** One immutable snapshot per host build/sort job, carried by its own native index buffer. */
public record WarpSortContext(WorldWarpFrame frame, WarpSection section) {
    public void attach(WarpNativeBuffer buffer) {
        buffer.overheaven$sortContext(frame,
                frame.camera().subtract(section.getOriginX(), section.getOriginY(), section.getOriginZ()));
    }
    public void tag(Object result) {
        if (result != null) ((WarpBuildOutput)result).overheaven$sorted(section, frame.epoch());
    }
}
