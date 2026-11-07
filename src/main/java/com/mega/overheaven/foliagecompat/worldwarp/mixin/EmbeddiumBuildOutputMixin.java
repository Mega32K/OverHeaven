package com.mega.overheaven.foliagecompat.worldwarp.mixin;

import com.mega.overheaven.foliagecompat.worldwarp.client.WarpBuildOutput;
import com.mega.overheaven.foliagecompat.worldwarp.client.WarpSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildOutput", remap = false)
public abstract class EmbeddiumBuildOutputMixin implements WarpBuildOutput {
    @Unique private WarpSection overheaven$section;
    @Unique private long overheaven$epoch;
    @Override public void overheaven$sorted(WarpSection section, long epoch) {
        overheaven$section = section;
        overheaven$epoch = epoch;
    }
    @Override public void overheaven$uploaded() {
        if (overheaven$section != null) overheaven$section.overheaven$warpSorted(overheaven$epoch);
    }
}
