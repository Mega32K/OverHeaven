package com.mega.overheaven.foliagecompat.finale;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

/** Continuity and cue invariants across normal, compressed and non-uniform server timelines. */
public final class FinaleChoreographyVerification {
    private static int checks;
    private static void check(boolean pass,String message) { if(!pass) throw new AssertionError(message); checks++; }
    public static void main(String[] args) throws Exception {
        var normal=FinaleTimeline.defaults();
        for(var timeline:new FinaleTimeline[]{normal,new FinaleTimeline(new int[]{20,20,20,20,20,20,20,20}),normal.with(FinaleTimeline.Phase.SUMMON,31.3).with(FinaleTimeline.Phase.FRACTURE,7.1).with(FinaleTimeline.Phase.UNWEAVE,90)}) {
            check(FinaleChoreography.motion(timeline,-100).equals(FinaleChoreography.motion(timeline,0)),"negative ticks clamp");
            check(FinaleChoreography.motion(timeline,timeline.total()+200).equals(FinaleChoreography.motion(timeline,timeline.total())),"finished ticks clamp");
            for(var phase:FinaleTimeline.Phase.values()) {
                double start=timeline.start(phase),duration=timeline.duration(phase),epsilon=.0001;
                if(start>0) {
                    var left=FinaleChoreography.motion(timeline,start-epsilon); var right=FinaleChoreography.motion(timeline,start+epsilon);
                    check(Math.abs(left.angle()-right.angle())<.0001,"angular position continuous "+phase);
                    check(Math.abs(left.speed()-right.speed())<.00001,"angular velocity continuous "+phase);
                    var starLeft=FinaleChoreography.starMotion(timeline,start-epsilon);var starRight=FinaleChoreography.starMotion(timeline,start+epsilon);
                    check(Math.abs(starLeft.angle()-starRight.angle())<.0001 && Math.abs(starLeft.speed()-starRight.speed())<.00001,"sky phase boundary continuous "+phase);
                    var previous=FinaleTimeline.Phase.values()[phase.ordinal()-1];
                    for(int layer=0;layer<4;layer++) check(Math.abs(FinaleChoreography.stem(layer,new FinaleTimeline.Frame(previous,1))-FinaleChoreography.stem(layer,new FinaleTimeline.Frame(phase,0)))<.00001,"stem envelope continuous "+phase+" layer="+layer);
                }
                for(double progress:new double[]{.01,.2,.5,.8,.99}) {
                    double ticks=start+duration*progress;
                    var motion=FinaleChoreography.motion(timeline,ticks);
                    double derivative=(FinaleChoreography.motion(timeline,ticks+epsilon).angle()-FinaleChoreography.motion(timeline,ticks-epsilon).angle())/(2*epsilon/20);
                    check(Double.isFinite(motion.angle()) && Math.abs(derivative-motion.speed())<.00001,"velocity is derivative of position "+phase);
                    for(int layer=0;layer<4;layer++) {
                        double gain=FinaleChoreography.stem(layer,timeline.frame(ticks)),pitch=FinaleChoreography.pitch(layer,timeline.frame(ticks));
                        check(Double.isFinite(gain) && gain>=0 && gain<=1 && pitch>0 && pitch<=2,"bounded sound layer "+phase);
                    }
                    check(motion.equals(FinaleChoreography.motion(timeline,ticks)),"pause and late join deterministic");
                    var starMotion=FinaleChoreography.starMotion(timeline,ticks);
                    double starDerivative=(FinaleChoreography.starMotion(timeline,ticks+epsilon).angle()-FinaleChoreography.starMotion(timeline,ticks-epsilon).angle())/(2*epsilon/20);
                    check(Math.abs(starDerivative-starMotion.speed())<.00001,"sky angular velocity matches integral "+phase);
                    if(phase.ordinal()<5)check(starMotion.equals(motion),"rotation before extinction unchanged");
                }
            }
            check(Math.abs(FinaleChoreography.motion(timeline,timeline.start(FinaleTimeline.Phase.SILENCE)).speed()+2.1)<1.e-8,"subject motion retains original terminal speed");
            double extinctionStart=timeline.start(FinaleTimeline.Phase.EXTINGUISH),extinctionDuration=timeline.duration(FinaleTimeline.Phase.EXTINGUISH);
            double previousSpeed=0;
            for(int i=0;i<=100;i++) {
                double progress=i/100.,tick=extinctionStart+extinctionDuration*progress;
                var motion=FinaleChoreography.starMotion(timeline,tick);
                check(Math.abs(motion.speed())>=previousSpeed-1.e-8,"extinction rotation accelerates monotonically");
                previousSpeed=Math.abs(motion.speed());
                if(progress>=.25) check(Math.abs(motion.speed()+8.4)<1.e-8,"4x terminal spin reached at quarter phase");
            }
            for(double progress:new double[]{.249999,.25,.250001}) {
                double tick=extinctionStart+extinctionDuration*progress,epsilon=.00001;
                double derivative=(FinaleChoreography.starMotion(timeline,tick+epsilon).angle()-FinaleChoreography.starMotion(timeline,tick-epsilon).angle())/(epsilon*.1);
                check(Math.abs(derivative-FinaleChoreography.starMotion(timeline,tick).speed())<.00001,"capped ramp keeps analytical angular integral");
            }
            check(FinaleChoreography.motion(timeline,timeline.start(FinaleTimeline.Phase.REVERSE)).speed()>0 && FinaleChoreography.motion(timeline,timeline.start(FinaleTimeline.Phase.UNWEAVE)).speed()<0,"outflow reverses with continuous momentum");
        }
        var ids=new HashSet<String>();
        for(var cue:FinaleChoreography.CUES) {
            check(cue.fraction()>0 && cue.fraction()<1 && cue.gain()>0 && cue.gain()<=1,"valid cue "+cue.sound());
            check(ids.add(cue.phase().id()+":"+cue.fraction()+":"+cue.sound()),"cue identity allows simultaneous glass and global impact");
            check(Files.exists(Path.of("src/main/resources/assets/overheaven_foliage_compat/sounds/finale",cue.sound()+".ogg")),"cue sound asset exists");
        }
        long impact=FinaleChoreography.CUES.stream().filter(cue->cue.phase()==FinaleTimeline.Phase.FRACTURE && cue.fraction()==.8 && !cue.glass()).count();
        check(impact==1,"no-glass show still has exactly one final energy impact");
        for(double progress:new double[]{.625,.8,1}) for(int layer=0;layer<4;layer++) check(FinaleChoreography.stem(layer,new FinaleTimeline.Frame(FinaleTimeline.Phase.SILENCE,progress))==0,"last three seconds have no sustained audio");
        System.out.println("Finale choreography: "+checks+" continuity, cue, pause, duration and silence checks passed");
    }
}
