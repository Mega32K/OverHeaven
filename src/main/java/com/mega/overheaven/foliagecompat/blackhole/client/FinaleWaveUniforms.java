package com.mega.overheaven.foliagecompat.blackhole.client;

import com.mega.overheaven.foliagecompat.finale.FinaleWaves;

final class FinaleWaveUniforms {
    static void apply(GlProgram p,FinaleWaves.State w) {
        p.integer("WavePresent",w.active()?1:0);p.integer("WaveFinal",w.last?1:0);
        p.scalar("WaveRadius",w.radius);p.scalar("WaveImpulse",w.impulse);p.scalar("WaveBreakup",w.breakup);
        p.scalar("WaveExtent",w.extent);p.scalar("WaveAge",w.age);p.scalar("WaveAmplitude",w.amplitude);p.scalar("WaveSupport",w.support);
    }
}
