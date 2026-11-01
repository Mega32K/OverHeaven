package com.mega.overheaven.foliagecompat.finale;

import java.util.Arrays;

/** Deterministic analytic exposure, with reusable angular samples and no image history. */
public final class FinaleStarExposure {
    public static final double MAX_ARC=Math.toRadians(24);
    public static final int HISTORY_SAMPLES=64;
    public final float[] full=new float[HISTORY_SAMPLES+1],travel=new float[HISTORY_SAMPLES+1],galaxy=new float[3];
    public double seconds,window,angle;
    public int segments,galaxySamples;
    public boolean enabled;
    public void clear() {
        seconds=window=angle=0;segments=galaxySamples=0;enabled=false;
        Arrays.fill(full,0);Arrays.fill(travel,0);Arrays.fill(galaxy,0);
    }
    public static double seconds(FinaleTimeline timeline,double ticks) {
        if(ticks<timeline.impactTicks()+10) return 0;
        var frame=timeline.frame(ticks);double p=frame.progress();
        return switch(frame.phase()) {
            case FRACTURE -> .025;
            case REVERSE -> .025+.055*FinaleTimeline.smooth(0,1,p);
            case UNWEAVE -> .08+.12*FinaleTimeline.smooth(0,1,p);
            case EXTINGUISH -> .20*(1-FinaleTimeline.smooth(.5,1,p));
            default -> 0;
        };
    }
    private static double angle(FinaleTimeline timeline,double ticks) { return FinaleChoreography.starMotion(timeline,ticks).angle(); }
    private static double travel(FinaleTimeline timeline,double start,double end) {
        var a=FinaleChoreography.starMotion(timeline,start);var b=FinaleChoreography.starMotion(timeline,end);
        if(a.speed()*b.speed()>=0) return Math.abs(b.angle()-a.angle());
        // The <=0.2 s window can contain only the reverse phase's single turning point.
        double left=start,right=end;
        for(int i=0;i<24;i++) {
            double mid=(left+right)*.5;
            if(FinaleChoreography.starMotion(timeline,mid).speed()*a.speed()>0) left=mid;else right=mid;
        }
        double turning=angle(timeline,(left+right)*.5);
        return Math.abs(turning-a.angle())+Math.abs(b.angle()-turning);
    }
    public void evaluate(FinaleTimeline timeline,double ticks,int detail) {
        clear();seconds=seconds(timeline,ticks);if(seconds<=0) return;
        enabled=true;segments=detail==0?4:detail==2?12:8;galaxySamples=detail==0?1:detail==2?3:2;
        angle=angle(timeline,ticks);
        window=Math.min(seconds*20,Math.max(0,ticks-(timeline.impactTicks()+10)));
        // The angle cap also recedes during extinction, so fast rotation cannot hold
        // a 24-degree trail while the authored shutter is already closing.
        double limit=MAX_ARC*(timeline.frame(ticks).phase()==FinaleTimeline.Phase.EXTINGUISH?seconds/.20:1);
        if(travel(timeline,ticks-window,ticks)>limit) {
            double low=0,high=window;
            for(int i=0;i<24;i++) { double middle=(low+high)*.5;if(travel(timeline,ticks-middle,ticks)>limit)high=middle;else low=middle; }
            window=low;
        }
        // A fixed time table lets each star use 55-85% of the same shutter without
        // scaling angles (which would distort acceleration and the reversal).
        double turningTick=Double.NaN,turningAngle=0;
        double initialSpeed=FinaleChoreography.starMotion(timeline,ticks-window).speed();
        if(initialSpeed*FinaleChoreography.starMotion(timeline,ticks).speed()<0) {
            double left=ticks-window,right=ticks;
            for(int i=0;i<24;i++) {
                double mid=(left+right)*.5;
                if(FinaleChoreography.starMotion(timeline,mid).speed()*initialSpeed>0)left=mid;else right=mid;
            }
            turningTick=(left+right)*.5;turningAngle=angle(timeline,turningTick);
        }
        for(int i=0;i<=HISTORY_SAMPLES;i++) {
            double sampleTick=ticks-window*i/HISTORY_SAMPLES,sampleAngle=angle(timeline,sampleTick);
            full[i]=(float)(sampleAngle-angle);
            travel[i]=(float)(sampleTick<turningTick
                ? Math.abs(turningAngle-angle)+Math.abs(sampleAngle-turningAngle):Math.abs(sampleAngle-angle));
        }
        // Galaxy shutter is one quarter of the bounded stellar exposure window.
        for(int i=0;i<galaxySamples;i++) galaxy[i]=(float)(angle(timeline,ticks-window*.25*(i+.5)/galaxySamples)-angle);
    }
}
