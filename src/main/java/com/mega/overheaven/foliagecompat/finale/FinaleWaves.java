package com.mega.overheaven.foliagecompat.finale;

/** One authoritative, allocation-free propagation model for the renderer and local camera. */
public final class FinaleWaves {
    public static final int COUNT=7;
    private static final double SPEED=1.5, RANGE=2, BOOST=1.25, IMPACT_SPEED=3, EXTRA=.75;
    private static final double EXPONENT=10*Math.log(2), EXPO_DENOMINATOR=1023;
    private static final double TRAVEL_INTEGRAL=1+EXTRA*(1/EXPONENT-1/EXPO_DENOMINATOR);
    private static final double[] START={.06,.37,.66,.10,.36,.62,.80};
    private static final double[] WINDOW={.25,.22,.19,.20,.18,.16,.20};
    private static final double[] AMPLITUDE={.75,1,1.25,.65,.9,1.25,2};
    public static final class State {
        public int id=-1;
        public double radius,impulse,breakup,extent,age,amplitude,support,start,end,velocity;
        public boolean last;
        public boolean active() { return id>=0; }
        public void clear() { id=-1; radius=impulse=breakup=extent=age=amplitude=support=velocity=0; last=false; }
    }
    private FinaleWaves() { }
    public static double start(FinaleTimeline timeline,int id) {
        var phase=id<3?FinaleTimeline.Phase.SUMMON:FinaleTimeline.Phase.FRACTURE;
        return timeline.start(phase)+timeline.duration(phase)*START[id];
    }
    /** Previous launch window in ticks; retained for calibration and older verification callers. */
    public static double duration(FinaleTimeline timeline,int id) {
        return timeline.duration(id<3?FinaleTimeline.Phase.SUMMON:FinaleTimeline.Phase.FRACTURE)*WINDOW[id]*.5/SPEED;
    }
    public static double extent(double radius,double outer,double thickness) {
        double support=Math.max(32,Math.max(outer*1.35+thickness*2,radius*1.5));
        return Math.min(Math.max(radius*2,Math.min(30,outer*1.30)),support*.97);
    }
    public static double terminal(double radius,double outer,double thickness) { return extent(radius,outer,thickness)*.86*RANGE; }
    public static double endVelocity(double radius,double extent,double duration) {
        return (extent*.86-radius*.28)*(.4*6*Math.exp(-6)/(1-Math.exp(-6))+.6)/(.91*duration);
    }
    /** Compression time; outward velocities below are measured in blocks per synchronized tick. */
    public static double chargeDuration(FinaleTimeline timeline,int id) { return duration(timeline,id)*.09/(BOOST*IMPACT_SPEED); }
    public static double baseVelocity(FinaleTimeline timeline,int id,double radius,double outer,double thickness) {
        double d=duration(timeline,id),e=extent(radius,outer,thickness);
        double oldFlight=d*.91+e*.86*(RANGE-1)/endVelocity(radius,e,d);
        return IMPACT_SPEED*BOOST*(terminal(radius,outer,thickness)-radius*.28)/oldFlight;
    }
    public static double easeInExpo(double u) { return Math.expm1(EXPONENT*Math.max(0,Math.min(1,u)))/EXPO_DENOMINATOR; }
    public static double speedIntegral(double u) {
        u=Math.max(0,Math.min(1,u));
        return u+EXTRA*(Math.expm1(EXPONENT*u)/EXPONENT-u)/EXPO_DENOMINATOR;
    }
    public static double flightDuration(FinaleTimeline timeline,int id,double radius,double outer,double thickness) {
        double distance=terminal(radius,outer,thickness)*(id==6?2:1)-radius*.28;
        return distance/(baseVelocity(timeline,id,radius,outer,thickness)*TRAVEL_INTEGRAL);
    }
    public static double end(FinaleTimeline timeline,int id,double radius,double outer,double thickness) {
        return start(timeline,id)+chargeDuration(timeline,id)+flightDuration(timeline,id,radius,outer,thickness);
    }
    public static void evaluate(FinaleTimeline timeline,double ticks,double radius,double outer,double thickness,State out) {
        out.clear();
        for(int id=0;id<COUNT;id++) if(ticks>=start(timeline,id) && ticks<end(timeline,id,radius,outer,thickness)) {
            sample(timeline,id,ticks,radius,outer,thickness,out); return;
        }
    }
    public static void sample(FinaleTimeline timeline,int id,double ticks,double radius,double outer,double thickness,State out) {
        double extent=extent(radius,outer,thickness),start=start(timeline,id);
        double charge=chargeDuration(timeline,id),flight=flightDuration(timeline,id,radius,outer,thickness);
        double elapsed=Math.max(0,ticks-start),u=Math.max(0,Math.min(1,(elapsed-charge)/flight));
        double end=start+charge+flight,age=Math.max(0,Math.min(1,elapsed/(end-start)));
        double base=baseVelocity(timeline,id,radius,outer,thickness);
        out.id=id; out.last=id==6; out.start=start; out.end=end;
        out.radius=ticks<start+charge?radius*(.44-.16*FinaleTimeline.smooth(0,1,elapsed/charge)):
                radius*.28+base*flight*speedIntegral(u);
        out.radius=Math.min(out.radius,terminal(radius,outer,thickness)*(out.last?2:1));
        out.velocity=ticks<start+charge?0:base*(1+EXTRA*easeInExpo(u));
        out.extent=extent*RANGE*(out.last?2:1); out.age=age; out.amplitude=AMPLITUDE[id];
        out.impulse=FinaleTimeline.smooth(0,.28,elapsed/charge)*(1.5-.65*FinaleTimeline.smooth(.09,.6,age))*(1-FinaleTimeline.smooth(.94,1,age));
        out.breakup=FinaleTimeline.smooth(.48,.88,age);
        // Include the scenery dispersion shell, which extends outside the luminous wave.
        double fringe=Math.max(radius*.90,out.radius*.36);
        out.support=Math.max(32,Math.max(outer*1.35+thickness*2,out.radius+Math.max(out.extent*.12+radius*.04,fringe)));
    }
    public static double flare(FinaleTimeline timeline,double ticks) {
        double peak=0;
        for(int id=0;id<COUNT;id++) {
            double seconds=(ticks-start(timeline,id))/20;
            if(seconds>=0 && seconds<.3) peak=Math.max(peak,FinaleTimeline.smooth(0,.025,seconds)*(1-FinaleTimeline.smooth(.025,.3,seconds)));
        }
        return peak;
    }
}
