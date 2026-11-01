package com.mega.overheaven.foliagecompat.finale;

import java.util.Arrays;

public final class FinaleStarExposureVerification {
    private static int checks;
    private static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message);checks++; }
    private static double shutter(double t) {double q=Math.max(0,Math.min(1,(t-.7)/.3));return (Math.min(t,.7)+.3*(q-q*q*q+.5*q*q*q*q))/.85;}
    public static void main(String[] args) {
        var state=new FinaleStarExposure();var repeated=new FinaleStarExposure();
        for(var timeline:new FinaleTimeline[]{FinaleTimeline.defaults(),new FinaleTimeline(new int[]{20,20,20,20,20,20,20,20}),FinaleTimeline.defaults().with(FinaleTimeline.Phase.REVERSE,70).with(FinaleTimeline.Phase.EXTINGUISH,1)}) {
            for(int detail=0;detail<3;detail++)for(double tick=0;tick<timeline.total()+1;tick+=.31) {
                state.evaluate(timeline,tick,detail);repeated.evaluate(timeline,tick,detail);
                check(Arrays.equals(state.full,repeated.full)&&Arrays.equals(state.travel,repeated.travel)&&Arrays.equals(state.galaxy,repeated.galaxy),"pause/late join reconstruct exactly");
                check(state.seconds>=0 && state.seconds<=.20000001 && state.window<=state.seconds*20+.000001,"bounded exposure window");
                if(!state.enabled) {check(state.window==0&&Arrays.equals(state.full,new float[65]),"no stale samples outside sky");continue;}
                check(state.segments==(detail==0?4:detail==2?12:8),"quality segment count");
                double travel=0;
                for(int i=0;i<=FinaleStarExposure.HISTORY_SAMPLES;i++) {
                    double expected=FinaleChoreography.starMotion(timeline,tick-state.window*i/FinaleStarExposure.HISTORY_SAMPLES).angle()-state.angle;
                    check(Math.abs(expected-state.full[i])<1.e-6,"sample is exact synchronized historical angle");
                    check(Float.isFinite(state.full[i])&&Float.isFinite(state.travel[i]),"finite angular samples");
                    if(i>0)check(state.travel[i]>=state.travel[i-1],"total angular travel monotone including reversal");
                    if(i>0)travel+=Math.abs(state.full[i]-state.full[i-1]);
                }
                check(state.travel[64]>=travel-1.e-6 && state.travel[64]<=FinaleStarExposure.MAX_ARC+1.e-6,"24-degree arc path budget including reverse");
                double mass=0;for(int i=0;i<state.segments;i++)mass+=shutter((i+1.)/state.segments)-shutter(i/(double)state.segments);
                check(Math.abs(mass-1)<1.e-12,"segment shutter weights conserve total energy");
            }
            for(var phase:new FinaleTimeline.Phase[]{FinaleTimeline.Phase.REVERSE,FinaleTimeline.Phase.UNWEAVE,FinaleTimeline.Phase.EXTINGUISH,FinaleTimeline.Phase.SILENCE}) {
                double tick=timeline.start(phase);state.evaluate(timeline,tick-.00001,1);double previous=state.seconds;state.evaluate(timeline,tick+.00001,1);
                check(Math.abs(previous-state.seconds)<1.e-6,"phase exposure envelope continuous");
            }
        }
        var timeline=FinaleTimeline.defaults();
        state.evaluate(timeline,42*20,1);check(Math.abs(state.seconds-.025)<1.e-12,"short opening shutter");
        state.evaluate(timeline,60*20,1);check(Math.abs(state.seconds-.08)<1.e-12,"reverse ends at 80ms");
        state.evaluate(timeline,93*20,1);check(Math.abs(state.seconds-.2)<1.e-12&&Math.abs(Math.abs(state.full[64])-FinaleStarExposure.MAX_ARC)<1.e-6,"fast rotation clamps angular span rather than extending budget");
        double last=Double.POSITIVE_INFINITY;
        for(double seconds=98;seconds<108;seconds+=.05) {state.evaluate(timeline,seconds*20,1);double span=Math.abs(state.full[64]);check(span<=last+1.e-7,"extinction latter half continuously shortens capped arcs");last=span;}
        state.evaluate(timeline,108*20,1);check(!state.enabled,"silence clears all exposure");
        for(double seconds=51;seconds<56;seconds+=.01) {
            state.evaluate(timeline,seconds*20,1);
            for(double fraction:new double[]{.55,.65,.7,.85,1}) {
                double at=fraction*64;int index=Math.min(63,(int)at);double f=at-index;
                double sample=state.full[index]*(1-f)+state.full[index+1]*f;
                double exact=FinaleChoreography.starMotion(timeline,seconds*20-state.window*fraction).angle()-state.angle;
                check(Math.abs(sample-exact)<1.e-5,"variable shutter interpolation follows real reversing angular motion");
            }
        }
        state.clear();check(!state.enabled&&state.segments==0&&state.galaxySamples==0,"resource reset clears state");
        System.out.println("PASS star exposure: "+checks+" mathematical and lifecycle assertions");
    }
}
