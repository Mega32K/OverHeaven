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
                for(double value:new double[]{state.strength,state.charge,state.unfold,state.tighten,state.capture,state.disk,state.reveal,state.burst,state.recoil,state.backgroundGain,state.revealGlare,state.impactFrame}) check(Double.isFinite(value)&&value>=0&&value<=1,"bounded transition");
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
                if(age>=.20-1e-10)check(state.impactFrame<1e-12,"impact frames clear at scaled .20 seconds");
                if(age<.135-1e-10)check(state.impactFrame>1-1e-12,"full contrast held for the authored impact beats");
            }
            CausalTransition.evaluate(timeline,duration*.5,1,state);check(state.backgroundGain==1&&state.revealGlare==0&&state.impactFrame==0,"recovery clears new layers");
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
        for(double seconds:new double[]{1,8,40}) {
            var timeline=normal.with(FinaleTimeline.Phase.EXPOSE,seconds);double duration=timeline.duration(FinaleTimeline.Phase.EXPOSE);
            for(double age:new double[]{-.001,.015,.055,.085,.115,.16,.201}) {
                CausalTransition.evaluate(timeline,duration*(.55+age/8),-1,state);
                if(age<0||age>=.20)check(state.impactFrame==0&&state.impactPolarity==0,"no impact outside reveal window");
                else check(state.impactPolarity==(age<.045||age>=.10?1:0),"white black white beat order");
                double frozen=state.impactFrame;CausalTransition.evaluate(timeline,duration*(.55+age/8),-1,state);
                check(state.impactFrame==frozen,"impact freeze and late join are deterministic");
                CausalTransition.evaluate(timeline,duration*(.55+age/8),1,state);check(state.impactFrame==0,"stop clears impact without replay");
            }
        }
        for(double seconds:new double[]{1,8,40}) {
            var timeline=normal.with(FinaleTimeline.Phase.EXPOSE,seconds);double duration=timeline.duration(FinaleTimeline.Phase.EXPOSE);
            double previous=1;
            for(int i=0;i<=500;i++) {double p=i/1000.;CausalTransition.evaluate(timeline,duration*p,-1,state);check(state.bodyScale>=previous-1e-12&&state.bodyScale<=1.25,"smooth expansion bounded at 125 percent");previous=state.bodyScale;}
            check(Math.abs(previous-1.25)<1e-12,"125 percent peak at normalized 4 seconds");
            for(int i=501;i<=550;i++) {double p=i/1000.;CausalTransition.evaluate(timeline,duration*p,-1,state);check(state.bodyScale<=previous+1e-12&&state.bodyScale>=.6-1e-12,"contraction monotonic to 60 percent");previous=state.bodyScale;}
            check(Math.abs(previous-.6)<1e-12,"contraction endpoint aligns with reveal at any duration");
            check(CausalTransition.bodyScale(.51)<1.,"fast initial contraction retained");
            check(CausalTransition.bodyScale(.54)-CausalTransition.bodyScale(.55)>.05,"last .08 seconds keep moving instead of settling");
            double h=1e-7,at=CausalTransition.RELEASE;
            double incoming=(CausalTransition.bodyScale(at)-CausalTransition.bodyScale(at-h))/h;
            double outgoing=(CausalTransition.bodyScale(at+h)-CausalTransition.bodyScale(at))/h;
            check(incoming<-.5&&Math.abs(incoming-outgoing)<.002,"disk value and velocity continuous into reveal");
            CausalTransition.evaluate(timeline,duration*(at-h),-1,state);double before=state.bodyScale*state.capture;
            CausalTransition.evaluate(timeline,duration*at,-1,state);double current=state.bodyScale*state.capture;
            CausalTransition.evaluate(timeline,duration*(at+h),-1,state);double after=state.bodyScale*state.capture;
            check(Math.abs((current-before)/h-(after-current)/h)<.004,"horizon value and velocity continuous into reveal");
            double previousCore=.6,previousDisk=1,previousReveal=0;
            for(int i=1;i<=200;i++) {
                double p=at+(CausalTransition.REVEALED-at)*i/200;
                CausalTransition.evaluate(timeline,duration*p,-1,state);
                check(state.bodyScale<previous&&state.bodyScale>=.18-1e-12,"disk keeps contracting through reveal");
                check(state.bodyScale*state.capture<previousCore&&state.capture>=0,"horizon keeps collapsing through reveal");
                check(state.disk<=previousDisk&&state.reveal>=previousReveal,"disk and singularity emission handoff is monotonic");
                previous=state.bodyScale;previousCore=state.bodyScale*state.capture;previousDisk=state.disk;previousReveal=state.reveal;
            }
            check(Math.abs(state.bodyScale-.18)<1e-12&&state.capture<1e-12&&state.disk<1e-10&&state.reveal>1-1e-12,"reveal completes without leftover disk");
            check(Math.abs(CausalTransition.bodyScale(CausalTransition.REVEALED-h)-.18)/h<.002,"disk motion ends smoothly with emission");
            double paused=state.bodyScale;CausalTransition.evaluate(timeline,duration*CausalTransition.REVEALED,-1,state);
            check(Math.abs(paused-state.bodyScale)<1e-12,"paused handoff stays deterministic");
            for(double recovery:new double[]{0,.2,.6,1}) {
                CausalTransition.evaluate(timeline,duration*.56,recovery,state);
                check(state.bodyScale>=.18&&state.bodyScale<=1&&state.capture>=0&&state.capture<=1,"stop recovers valid size during handoff");
            }
            CausalTransition.evaluate(timeline,duration*.5,1,state);check(state.bodyScale==1,"stop restores original scale");
            CausalTransition.evaluate(timeline,duration,-1,state);check(state.bodyScale==1,"no scale modifier after opening");
        }
        for(int layer=0;layer<4;layer++) check(Math.abs(CausalTransition.stem(layer,1)-FinaleChoreography.stem(layer,new FinaleTimeline.Frame(FinaleTimeline.Phase.SUMMON,0)))<1e-9,"audio continuous into summon");
        check(CausalTransition.stem(1,.54)<.03,"audio vacuum before release");
        System.out.println("Causal transition: "+checks+" timing, recovery, camera, audio and synchronization checks passed");
    }
}
