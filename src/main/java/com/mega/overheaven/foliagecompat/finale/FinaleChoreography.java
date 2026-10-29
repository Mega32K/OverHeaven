package com.mega.overheaven.foliagecompat.finale;

import java.util.List;

public final class FinaleChoreography {
    private static final double[] SPEEDS={.008,.028,.12,.32,-.26,-.82,-2.1,-.025,0};
    public record Motion(double angle,double speed) { }
    public record Cue(FinaleTimeline.Phase phase,double fraction,String sound,boolean glass,float gain) { }
    public static final List<Cue> CUES=List.of(
        new Cue(FinaleTimeline.Phase.EXPOSE,.02,"causal_charge",false,.48f),
        new Cue(FinaleTimeline.Phase.EXPOSE,.175,"causal_fault",false,.38f),
        new Cue(FinaleTimeline.Phase.EXPOSE,.265,"causal_fault",false,.48f),
        new Cue(FinaleTimeline.Phase.EXPOSE,.355,"causal_fault",false,.58f),
        new Cue(FinaleTimeline.Phase.EXPOSE,CausalTransition.RELEASE,"causal_release",false,.85f),
        new Cue(FinaleTimeline.Phase.EXPOSE,CausalTransition.REVEALED,"causal_recoil",false,.64f),
        new Cue(FinaleTimeline.Phase.SUMMON,.06,"pulse",false,.48f),
        new Cue(FinaleTimeline.Phase.SUMMON,.37,"pulse",false,.62f),
        new Cue(FinaleTimeline.Phase.SUMMON,.66,"pulse",false,.78f),
        new Cue(FinaleTimeline.Phase.FRACTURE,.10,"crack",true,.35f),
        new Cue(FinaleTimeline.Phase.FRACTURE,.36,"crack",true,.5f),
        new Cue(FinaleTimeline.Phase.FRACTURE,.62,"crack",true,.66f),
        new Cue(FinaleTimeline.Phase.FRACTURE,.80,"rupture",true,.76f),
        new Cue(FinaleTimeline.Phase.FRACTURE,.80,"impact",false,.8f),
        new Cue(FinaleTimeline.Phase.REVERSE,.12,"reverse",false,.65f),
        new Cue(FinaleTimeline.Phase.REVERSE,.93,"time_break",false,.6f),
        new Cue(FinaleTimeline.Phase.UNWEAVE,.24,"fold",false,.5f),
        new Cue(FinaleTimeline.Phase.UNWEAVE,.58,"fold",false,.6f),
        new Cue(FinaleTimeline.Phase.UNWEAVE,.84,"fold",false,.72f));
    private FinaleChoreography() { }
    public static Motion motion(FinaleTimeline timeline,double ticks) { return motion(timeline,ticks,false); }
    public static Motion starMotion(FinaleTimeline timeline,double ticks) { return motion(timeline,ticks,true); }
    private static Motion motion(FinaleTimeline timeline,double ticks,boolean sky) {
        double remaining=Math.max(0,Math.min(timeline.total(),ticks)),angle=0,speed=SPEEDS[0];
        for(var phase:FinaleTimeline.Phase.values()) {
            int index=phase.ordinal(); double duration=timeline.duration(phase),progress=Math.min(1,remaining/duration);
            // EXTINGUISH reaches its 4x terminal speed in the first quarter of the phase.
            // Integrate the capped speed ramp so angle, pause and phase joins remain continuous.
            double rate=sky && phase==FinaleTimeline.Phase.EXTINGUISH?4:1;
            double ramp=Math.min(1,progress*rate);
            double integral=ramp*ramp*ramp*(1-ramp*.5)/rate+Math.max(0,progress-1/rate);
            double from=sky && index==6?SPEEDS[index]*4:SPEEDS[index];
            double to=sky && index+1==6?SPEEDS[index+1]*4:SPEEDS[index+1];
            angle+=duration/20*(from*progress+(to-from)*integral);
            speed=from+(to-from)*FinaleTimeline.smooth(0,1,ramp);
            if(remaining<=duration) break;
            remaining-=duration;
        }
        return new Motion(angle,speed);
    }
    public static double stem(int layer,FinaleTimeline.Frame frame) {
        double progress=frame.progress();
        return switch(frame.phase()) {
            case EXPOSE -> CausalTransition.stem(layer,progress);
            case SUMMON -> switch(layer) { case 0 -> .42; case 1 -> .34*FinaleTimeline.smooth(0,1,progress); case 2 -> .12*progress; default -> .08+.12*progress; };
            case FRACTURE -> switch(layer) { case 0 -> .42*(1-.65*FinaleTimeline.smooth(.8,1,progress)); case 1 -> (.34+.16*progress)*(1-FinaleTimeline.smooth(.8,.88,progress)); case 2 -> .12+.16*FinaleTimeline.smooth(0,.78,progress); default -> .20; };
            case REVERSE -> switch(layer) { case 0 -> .147*(1-progress); case 1 -> .22*FinaleTimeline.smooth(.16,.4,progress)*(1-progress); case 2 -> .28*(1-.7*progress); default -> .2+.18*FinaleTimeline.smooth(.12,.75,progress); };
            case UNWEAVE -> switch(layer) { case 1 -> .14*Math.sin(progress*Math.PI); case 2 -> .084+.20*FinaleTimeline.smooth(0,1,progress); case 3 -> .38+.08*Math.sin(progress*Math.PI); default -> 0; };
            case EXTINGUISH -> switch(layer) { case 1 -> .09*Math.sin(progress*Math.PI)*(1-FinaleTimeline.smooth(.22,.68,progress)); case 2 -> .284*(1-FinaleTimeline.smooth(.08,.65,progress)); case 3 -> .007+.373*(1-FinaleTimeline.smooth(.25,.97,progress)); default -> 0; };
            case SILENCE -> layer==3?.007*(1-FinaleTimeline.smooth(0,.38,progress)):0;
            case RETURN -> 0;
        };
    }
    public static double pitch(int layer,FinaleTimeline.Frame frame) {
        double progress=frame.progress();
        int phase=frame.phase().ordinal();
        if(phase==0) return layer==0?1+.025*Math.sin(progress*19)*(1-FinaleTimeline.smooth(.6,1,progress)):1+.25*FinaleTimeline.smooth(.175,.53,progress)*(1-FinaleTimeline.smooth(.57,.825,progress));
        if(layer==0) return phase<3?1+.10*FinaleTimeline.smooth(0,3,phase+progress):1.1-.48*FinaleTimeline.smooth(0,1,progress);
        if(layer==2) return phase<3?.65+.35*FinaleTimeline.smooth(0,3,phase+progress):phase==3?1-.45*progress:phase==4?.55+.85*progress:1.4-.85*progress;
        return 1;
    }
}
