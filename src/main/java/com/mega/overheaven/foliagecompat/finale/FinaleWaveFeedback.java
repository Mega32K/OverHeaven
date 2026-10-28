package com.mega.overheaven.foliagecompat.finale;

import java.util.Arrays;

/** Local crossing history only; no player mutation, wall clock or network additions. */
public final class FinaleWaveFeedback {
    private final boolean[] seen=new boolean[FinaleWaves.COUNT];
    private final double[] hits=new double[FinaleWaves.COUNT];
    private final FinaleWaves.State wave=new FinaleWaves.State();
    private double previousTicks=Double.NaN,x,y,z,previousDistance;
    private boolean wasPaused;
    public double pitch,yaw,roll,exposure,chroma;
    public FinaleWaveFeedback() { reset(); }
    public void reset() { Arrays.fill(seen,false); Arrays.fill(hits,Double.NaN); previousTicks=Double.NaN; wasPaused=false; zero(); }
    private void zero() { pitch=yaw=roll=exposure=chroma=0; }
    public void update(FinaleTimeline timeline,double ticks,double radius,double outer,double thickness,
                       double cameraX,double cameraY,double cameraZ,double distance,boolean paused,boolean discontinuity) {
        zero();
        double delta=ticks-previousTicks, movement=Math.sqrt(sq(cameraX-x)+sq(cameraY-y)+sq(cameraZ-z));
        boolean fresh=!Double.isFinite(previousTicks)||discontinuity||delta<-.001||movement>Math.max(4,Math.max(0,delta)*2);
        if(fresh) {
            Arrays.fill(hits,Double.NaN);
            for(int id=0;id<FinaleWaves.COUNT;id++) {
                if(ticks>=FinaleWaves.end(timeline,id,radius,outer,thickness)) seen[id]=true;
                else if(ticks>=FinaleWaves.start(timeline,id)) {
                    FinaleWaves.sample(timeline,id,ticks,radius,outer,thickness,wave);
                    if(distance<=wave.radius) seen[id]=true;
                }
            }
        } else if(!paused && !wasPaused && delta>0) {
            for(int id=0;id<FinaleWaves.COUNT;id++) {
                if(seen[id]) continue;
                double start=FinaleWaves.start(timeline,id),end=FinaleWaves.end(timeline,id,radius,outer,thickness);
                if(ticks<start || previousTicks>=end) continue;
                double lo=Math.max(previousTicks,start),hi=Math.min(ticks,end);
                double d0=previousDistance+(distance-previousDistance)*(lo-previousTicks)/delta;
                double d1=previousDistance+(distance-previousDistance)*(hi-previousTicks)/delta;
                FinaleWaves.sample(timeline,id,lo,radius,outer,thickness,wave); double gap0=d0-wave.radius;
                FinaleWaves.sample(timeline,id,hi,radius,outer,thickness,wave); double gap1=d1-wave.radius;
                if(gap0>0 && gap1<=0) {
                    // Bisection against the moving camera segment, including low frame-rate crossings.
                    double left=lo,right=hi;
                    for(int step=0;step<18;step++) {
                        double mid=(left+right)*.5,d=previousDistance+(distance-previousDistance)*(mid-previousTicks)/delta;
                        FinaleWaves.sample(timeline,id,mid,radius,outer,thickness,wave);
                        if(d>wave.radius) left=mid; else right=mid;
                    }
                    hits[id]=(left+right)*.5; seen[id]=true;
                }
            }
        }
        for(int id=0;id<FinaleWaves.COUNT;id++) if(Double.isFinite(hits[id])) {
            double t=(ticks-hits[id])/20,duration=id==6?.5:.35;
            if(t>=0 && t<duration) {
                double envelope=FinaleTimeline.smooth(0,.018,t)*Math.pow(1-t/duration,2);
                double signal=Math.sin(t*2*Math.PI*13)*.72+Math.sin(t*2*Math.PI*23)*.28;
                pitch+=signal*envelope*(id==6?1.6:1);
                yaw+=Math.sin(t*2*Math.PI*17)*envelope*(id==6?.9:.6);
                roll+=Math.sin(t*2*Math.PI*11)*envelope*(id==6?.7:.45);
            }
            if(t>=0 && t<.2) exposure=Math.max(exposure,(id==6?.9:.6)*FinaleTimeline.smooth(0,.02,t)*(1-FinaleTimeline.smooth(.02,.2,t)));
            if(id==6 && t>=0 && t<.4) chroma=1-FinaleTimeline.smooth(0,.4,t);
        }
        if(!seen[6] && ticks>=FinaleWaves.start(timeline,6) && ticks<FinaleWaves.end(timeline,6,radius,outer,thickness)) {
            FinaleWaves.sample(timeline,6,ticks,radius,outer,thickness,wave);
            double gap=distance-wave.radius;
            if(gap>0) chroma=(1-FinaleTimeline.smooth(0,12,gap))*(1-FinaleTimeline.smooth(.94,1,wave.age));
        }
        pitch=clamp(pitch,1.6);yaw=clamp(yaw,.9);roll=clamp(roll,.7);
        previousTicks=ticks;previousDistance=distance;x=cameraX;y=cameraY;z=cameraZ;wasPaused=paused;
    }
    public boolean crossed(int id) { return Double.isFinite(hits[id]); }
    private static double sq(double a) { return a*a; }
    private static double clamp(double a,double max) { return Math.max(-max,Math.min(max,a)); }
}
