package com.mega.overheaven.foliagecompat.blackhole.client;

/** Deterministic eight-second material cohorts. No accumulated float time or frame history. */
final class DiskAdvection {
    static final double PERIOD=8,TAU=Math.PI*2;
    final double[] age=new double[2],anchor=new double[2],offset=new double[2];
    double blend,flow;
    double clockAge,clockCycle,clockAnchor;
    void evaluate(double time,double rotation,double infall,double span,long seed) {
        flow=fraction(time*infall/Math.max(.001,span));
        double cycleBase=Math.floor(time/PERIOD);
        clockAge=time-cycleBase*PERIOD;clockCycle=cycleBase-Math.floor(cycleBase/65536)*65536;
        clockAnchor=fraction(cycleBase*PERIOD*rotation/TAU);
        if(rotation==0) { java.util.Arrays.fill(age,0);java.util.Arrays.fill(anchor,0);java.util.Arrays.fill(offset,0);blend=1;return; }
        for(int i=0;i<2;i++) {
            double cycle=Math.floor((time+4-i*4)/PERIOD);
            double center=cycle*PERIOD+i*4;
            age[i]=time-center;
            anchor[i]=fraction(center*rotation/TAU);
            long hash=(long)cycle*0x9e3779b97f4a7c15L+seed+i*0x632be59bd9b4e019L;
            hash=(hash^(hash>>>30))*0xbf58476d1ce4e5b9L;
            hash=(hash^(hash>>>27))*0x94d049bb133111ebL;
            offset[i]=((hash^(hash>>>31))>>>40)/16777216.0;
        }
        double phase=Math.cos(Math.PI*age[0]/PERIOD);blend=phase*phase;
    }
    private static double fraction(double value) { return value-Math.floor(value); }
    void apply(GlProgram p) {
        p.integer("DiskRenewal",1);
        p.vector4("DiskPhase0",age[0],anchor[0],offset[0],blend);
        p.vector4("DiskPhase1",age[1],anchor[1],offset[1],flow);
        p.vector4("DiskClock",clockAge,clockCycle,clockAnchor,flow);
    }
}
