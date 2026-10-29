package com.mega.overheaven.foliagecompat.finale;

/** Pure synchronized choreography. No packet, world mutation, or client dependencies. */
public final class CausalTransition {
    public static final double RELEASE=.55, REVEALED=.56875;
    public static final class State {
        public double progress, strength, charge, unfold, tighten, reveal, capture, disk, recoil, motion, burst, flow;
        public double backgroundGain=1, bodyScale=1, revealGlare, impactFrame, impactPolarity;
        public boolean active;
    }
    private CausalTransition() { }
    private static double s(double a,double b,double x) { return FinaleTimeline.smooth(a,b,x); }
    public static double reveal(double p) { return s(RELEASE,REVEALED,p); }
    private static final double SHRINK_START=.5, PEAK_SCALE=1.25, RELEASE_SCALE=.6, DISK_END_SCALE=.18;
    private static final double EXPO_WEIGHT=.55, EXPO_RATE=10*Math.log(2), EXPO_NORMALIZER=1-1.0/1024;
    // Retain inward velocity at release, rather than settling at 60% before revealing the core.
    private static final double RELEASE_SLOPE=-(PEAK_SCALE-RELEASE_SCALE)
            *(EXPO_WEIGHT*EXPO_RATE/1023+1-EXPO_WEIGHT)/(RELEASE-SHRINK_START);
    /** Fast contraction with a moving tail, continued into the reveal with matching velocity. */
    public static double bodyScale(double progress) {
        double p=Math.max(0,progress);
        if(p<=SHRINK_START) return 1+(PEAK_SCALE-1)*(1-Math.cos(Math.PI*p/SHRINK_START))*.5;
        if(p<RELEASE) {
            double u=(p-SHRINK_START)/(RELEASE-SHRINK_START);
            double exponential=-Math.expm1(-EXPO_RATE*u)/EXPO_NORMALIZER;
            return PEAK_SCALE-(PEAK_SCALE-RELEASE_SCALE)*(EXPO_WEIGHT*exponential+(1-EXPO_WEIGHT)*u);
        }
        if(p>=REVEALED) return DISK_END_SCALE;
        double u=(p-RELEASE)/(REVEALED-RELEASE),u2=u*u,u3=u2*u;
        // Cubic Hermite: same value/velocity at 4.4s, zero velocity when disk emission ends.
        return (2*u3-3*u2+1)*RELEASE_SCALE+(u3-2*u2+u)*RELEASE_SLOPE*(REVEALED-RELEASE)
                +(-2*u3+3*u2)*DISK_END_SCALE;
    }
    public static void evaluate(FinaleTimeline timeline,double ticks,double recovery,State out) {
        double p=Math.max(0,ticks/timeline.duration(FinaleTimeline.Phase.EXPOSE));
        double keep=recovery<0?1:1-s(0,1,recovery);
        out.progress=Math.min(1,p); out.active=p<1 && keep>0;
        out.reveal=reveal(p)*keep;
        out.bodyScale=1+(bodyScale(p)-1)*keep;
        out.capture=1-(1-Math.pow(1-reveal(p),1.7))*keep;
        out.disk=1-(1-Math.pow(1-reveal(p),.72))*keep;
        out.charge=s(0,.4375,p)*(1-s(.56875,.825,p))*keep;
        out.unfold=s(.14,.4375,p)*(1-s(.70,1,p))*keep;
        out.tighten=s(.4375,.535,p)*(1-s(.55,.64,p))*keep;
        out.recoil=s(.55,.56875,p)*(1-s(.59,.825,p))*keep;
        out.strength=s(0,.175,p)*(1-.92*s(.585,.825,p))*(1-s(.825,1,p))*keep;
        out.burst=s(.55,.55625,p)*(1-s(.56875,.625,p))*keep;
        double releaseAge=(p-RELEASE)*8;
        double restore=1-Math.pow(1-Math.max(0,Math.min(1,releaseAge/.18)),3);
        out.backgroundGain=1-.75*s(0,RELEASE,p)*(1-restore)*keep;
        out.revealGlare=releaseAge<=0 || releaseAge>=.18?0:
                s(0,.02,releaseAge)*(1-s(.035,.18,releaseAge))*s(0,.08,reveal(p))*keep;
        // One authored white/black/white impact sequence, evaluated from synchronized time.
        out.impactFrame=releaseAge<0 || releaseAge>=.20?0:(1-s(.135,.20,releaseAge))*keep;
        out.impactPolarity=out.impactFrame>0 && (releaseAge<.045 || releaseAge>=.10)?1:0;
        // Freeze only the artistic field in the last .12 default seconds; never world time.
        out.motion=p<.535?p:p<.55?.535+(p-.535)*.035:p-.014475;
        // Integral of the acceleration ramp avoids texture velocity jumps.
        double q=Math.min(out.motion,.55); out.flow=(q+8*q*q*q/.9075)*keep;
        if(!out.active) { out.charge=out.unfold=out.tighten=out.recoil=out.strength=out.burst=out.flow=0; out.backgroundGain=out.bodyScale=1;out.revealGlare=out.impactFrame=out.impactPolarity=0; }
    }
    public static double exposure(FinaleTimeline timeline,double ticks,double recovery) {
        double p=ticks/timeline.duration(FinaleTimeline.Phase.EXPOSE), age=(p-RELEASE)*8;
        if(age<=0 || age>=.18) return 0;
        return .72*s(0,.018,age)*(1-s(.025,.18,age))*(recovery<0?1:1-s(0,1,recovery));
    }
    public static double stem(int layer,double p) {
        double vacuum=1-.97*s(.526,.535,p)*(1-s(.55,.56875,p));
        double charge=s(.025,.4375,p)*(1-s(.56875,.825,p));
        return vacuum*switch(layer) {
            case 0 -> .42*s(0,.08,p);
            case 1 -> .48*charge;
            case 2 -> .26*s(.175,.4375,p)*(1-s(.57,.825,p));
            default -> .08*s(0,.175,p)+.23*s(.28,.52,p)*(1-s(.56875,.825,p));
        };
    }
    /** One impulse per observed crossing; discontinuities consume past cues without replay. */
    public static final class Feedback {
        private double cursor=Double.NaN;
        private boolean fired, running;
        public double pitch,yaw,roll;
        public void reset() { cursor=Double.NaN; fired=running=false; pitch=yaw=roll=0; }
        public void update(FinaleTimeline timeline,double ticks,boolean discontinuity) {
            double at=timeline.duration(FinaleTimeline.Phase.EXPOSE)*RELEASE;
            if(Double.isNaN(cursor) || discontinuity || ticks<cursor || ticks-cursor>5) {
                fired|=ticks>=at; running=false;
            } else if(!fired && cursor<at && ticks>=at) { fired=true; running=true; }
            cursor=ticks; pitch=yaw=roll=0;
            double age=(ticks-at)/timeline.duration(FinaleTimeline.Phase.EXPOSE)*8;
            if(!running || age<0 || age>=.4) { running=false; return; }
            double envelope=s(0,.012,age)*(1-s(.02,.4,age));
            double kick=Math.cos(age*25)*envelope;
            pitch=1.2*kick; yaw=.7*Math.sin(age*27+.65)*envelope; roll=.5*Math.sin(age*24+.9)*envelope;
        }
    }
}
