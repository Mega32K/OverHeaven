package com.mega.overheaven.foliagecompat.finale;

/** Pure synchronized choreography. No packet, world mutation, or client dependencies. */
public final class CausalTransition {
    public static final double RELEASE=.55, REVEALED=.56875;
    public static final class State {
        public double progress, strength, charge, unfold, tighten, reveal, capture, disk, recoil, motion, burst, flow;
        public double backgroundGain=1, revealGlare, radialImpact;
        public boolean active;
    }
    private CausalTransition() { }
    private static double s(double a,double b,double x) { return FinaleTimeline.smooth(a,b,x); }
    public static double reveal(double p) { return s(RELEASE,REVEALED,p); }
    public static void evaluate(FinaleTimeline timeline,double ticks,double recovery,State out) {
        double p=Math.max(0,ticks/timeline.duration(FinaleTimeline.Phase.EXPOSE));
        double keep=recovery<0?1:1-s(0,1,recovery);
        out.progress=Math.min(1,p); out.active=p<1 && keep>0;
        out.reveal=reveal(p)*keep;
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
        out.radialImpact=releaseAge<=0 || releaseAge>=.22?0:
                s(0,.02,releaseAge)*(1-s(.035,.22,releaseAge))*keep;
        // Freeze only the artistic field in the last .12 default seconds; never world time.
        out.motion=p<.535?p:p<.55?.535+(p-.535)*.035:p-.014475;
        // Integral of the acceleration ramp avoids texture velocity jumps.
        double q=Math.min(out.motion,.55); out.flow=(q+8*q*q*q/.9075)*keep;
        if(!out.active) { out.charge=out.unfold=out.tighten=out.recoil=out.strength=out.burst=out.flow=0; out.backgroundGain=1;out.revealGlare=out.radialImpact=0; }
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
