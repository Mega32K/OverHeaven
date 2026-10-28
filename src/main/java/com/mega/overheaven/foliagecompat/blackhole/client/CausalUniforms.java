package com.mega.overheaven.foliagecompat.blackhole.client;

import com.mega.overheaven.foliagecompat.finale.CausalTransition;

final class CausalUniforms {
    private static final CausalTransition.State STATE=new CausalTransition.State();
    private CausalUniforms() { }
    static boolean sceneActive(float partial) {
        if(!FinaleClient.active()) return false;
        CausalTransition.evaluate(FinaleClient.state().timeline(),FinaleClient.ticks(partial),FinaleClient.stopping(partial),STATE);
        return STATE.active && (STATE.backgroundGain<1 || STATE.radialImpact>0);
    }
    static double glare(String id,float partial) {
        if(!FinaleClient.source(id)) return 0;
        CausalTransition.evaluate(FinaleClient.state().timeline(),FinaleClient.ticks(partial),FinaleClient.stopping(partial),STATE);
        return STATE.revealGlare;
    }
    static double exposure(float partial) {
        return FinaleClient.active()?CausalTransition.exposure(FinaleClient.state().timeline(),FinaleClient.ticks(partial),FinaleClient.stopping(partial)):0;
    }
    static void subject(GlProgram p,String id,float partial) {
        if(!FinaleClient.source(id)) { p.integer("IntroEnabled",0); p.scalar("IntroBurst",0); return; }
        CausalTransition.evaluate(FinaleClient.state().timeline(),FinaleClient.ticks(partial),FinaleClient.stopping(partial),STATE);
        apply(p,STATE);
    }
    static void apply(GlProgram p,CausalTransition.State s) {
        p.integer("IntroEnabled",s.active?1:0);
        p.scalar("IntroCharge",s.charge); p.scalar("IntroTighten",s.tighten);
        p.scalar("IntroCapture",s.capture); p.scalar("IntroDisk",s.disk); p.scalar("IntroBurst",s.burst);
        p.scalar("IntroFlow",s.flow);
        p.scalar("IntroBackgroundGain",s.backgroundGain);p.scalar("IntroRadialImpact",s.radialImpact);
    }
}
