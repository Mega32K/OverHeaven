package com.mega.overheaven.foliagecompat.finale;

public final class CausalTransitionVerification {
    private static int checks;
    private static void check(boolean pass,String name) { if(!pass) throw new AssertionError(name); checks++; }
    public static void main(String[] args) {
        var normal=FinaleTimeline.defaults(); var state=new CausalTransition.State();
        check(normal.total()==2400 && normal.impactTicks()==808,"120 seconds and 40.4 second impact unchanged");
        for(int duration:new int[]{20,40,160,800,12000}) {
            var timeline=normal.with(FinaleTimeline.Phase.EXPOSE,duration/20.0);
            for(int i=0;i<=1200;i++) {
                double p=i/1000.0;
                CausalTransition.evaluate(timeline,duration*p,-1,state);
                for(double value:new double[]{state.strength,state.charge,state.unfold,state.tighten,state.capture,state.disk,state.reveal,state.burst,state.recoil,state.backgroundGain,state.revealGlare,state.radialImpact}) check(Double.isFinite(value)&&value>=0&&value<=1,"bounded transition");
                if(p<=.55) check(state.reveal==0&&state.capture==1&&state.disk==1,"dark horizon retained before release");
                if(p>=.56875) check(state.reveal==1&&state.capture==0&&state.disk==0,"reveal complete");
                if(p>=1) check(!state.active&&state.strength==0&&state.burst==0,"no new effects after expose");
                check(timeline.flash(duration*p)==0 && timeline.takeover(duration*p)==0 || p>1,"no early cosmic takeover");
                double exposure=CausalTransition.exposure(timeline,duration*p,-1);
                check(exposure>=0&&exposure<=.72,"bounded exposure, no white flash");
                if(p<=.55||p>=.5725) check(exposure==0,"exposure only in release window");
            }
            CausalTransition.evaluate(timeline,0,-1,state);check(state.backgroundGain==1,"initial scene unchanged");
            CausalTransition.evaluate(timeline,duration*.55,-1,state);check(state.backgroundGain==.25,"minimum background exactly one quarter");
            double last=.25;
            for(int i=0;i<=220;i++) {
                double age=i*.001;CausalTransition.evaluate(timeline,duration*(.55+age/8),-1,state);
                check(state.backgroundGain>=last-1e-12,"rapid recovery monotonic");last=state.backgroundGain;
                if(age>=.18-1e-10)check(state.backgroundGain>1-1e-12&&state.revealGlare<1e-12,"gain restored and flash gone at scaled .18 seconds");
                if(age>=.22-1e-10)check(state.radialImpact<1e-12,"radial pulse gone at scaled .22 seconds");
            }
            CausalTransition.evaluate(timeline,duration*.5,1,state);check(state.backgroundGain==1&&state.revealGlare==0&&state.radialImpact==0,"recovery clears new layers");
            CausalTransition.evaluate(timeline,duration*.5,1,state);check(!state.active&&state.reveal==0&&state.capture==1&&state.strength==0,"stop restores original hole and environment");
            var feedback=new CausalTransition.Feedback(); double at=duration*.55;
            feedback.update(timeline,at-.05,false);feedback.update(timeline,at+duration*.004,false);
            check(Math.abs(feedback.pitch)<=1.2&&Math.abs(feedback.yaw)<=.7&&Math.abs(feedback.roll)<=.5,"camera caps");
            if(duration<800) check(Math.abs(feedback.pitch)>0,"observed release impulse");
            double frozen=feedback.pitch;feedback.update(timeline,at+duration*.004,false);check(feedback.pitch==frozen,"pause freezes feedback");
            feedback.reset();feedback.update(timeline,at+duration*.004,false);check(feedback.pitch==0,"late join skips old impulse");
            feedback.reset();feedback.update(timeline,at-.1,false);feedback.update(timeline,at+.1,true);check(feedback.pitch==0,"teleport or sync jump consumes release");
            feedback.update(timeline,duration,false);check(feedback.pitch==0&&feedback.yaw==0&&feedback.roll==0,"camera clears at end");
        }
        for(int layer=0;layer<4;layer++) check(Math.abs(CausalTransition.stem(layer,1)-FinaleChoreography.stem(layer,new FinaleTimeline.Frame(FinaleTimeline.Phase.SUMMON,0)))<1e-9,"audio continuous into summon");
        check(CausalTransition.stem(1,.54)<.03,"audio vacuum before release");
        System.out.println("Causal transition: "+checks+" timing, recovery, camera, audio and synchronization checks passed");
    }
}
