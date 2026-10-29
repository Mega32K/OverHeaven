package com.mega.overheaven.foliagecompat;

import com.mega.overheaven.foliagecompat.finale.*;

public final class FinaleFeedbackVerification {
    private static int checks;
    private static void check(boolean value,String description) { if(!value) throw new AssertionError(description);checks++; }
    private static boolean near(double a,double b) { return Math.abs(a-b)<1.e-6; }
    public static void main(String[] args) {
        var timeline=FinaleTimeline.defaults();var state=new FinaleWaves.State();
        check(FinaleWaves.easeInExpo(0)==0 && FinaleWaves.easeInExpo(1)==1,"normalized IN_EXPO endpoints");
        for(double radius:new double[]{.1,3.2,20,80}) for(double outer:new double[]{radius+2,radius+12,128}) {
            if(outer<=radius) continue;
            for(int id=0;id<7;id++) {
                double start=FinaleWaves.start(timeline,id),charge=FinaleWaves.chargeDuration(timeline,id);
                double flight=FinaleWaves.flightDuration(timeline,id,radius,outer,.16),base=FinaleWaves.baseVelocity(timeline,id,radius,outer,.16);
                double d=FinaleWaves.duration(timeline,id),e=FinaleWaves.extent(radius,outer,.16);
                double oldFlight=d*.91+e*.86/FinaleWaves.endVelocity(radius,e,d);
                check(near(charge,d*.09/(1.25*3)),"charge three times faster than previous revision");
                check(near(base,3*1.25*(e*1.72-radius*.28)/oldFlight),"all seven waves three times previous velocity");
                check(flight<oldFlight*(id==6?2.1:1),"outward lifetime shortened");
                double previous=-1,integral=0;
                for(int i=0;i<=1000;i++) {
                    double u=i/1000.;FinaleWaves.sample(timeline,id,start+charge+flight*u,radius,outer,.16,state);
                    check(state.velocity>=previous-1e-9,"outward speed monotonically increases");previous=state.velocity;
                    check(near(state.velocity,base*(1+.75*(Math.pow(2,10*u)-1)/1023)),"velocity follows IN_EXPO");
                    check(state.support>=state.radius+state.extent*.12,"wave within culling support");
                    integral+=state.velocity*flight/1000*(i==0||i==1000?.5:1);
                    if(i>0&&i<1000) {
                        double saved=state.radius,epsilon=flight*.00001;
                        FinaleWaves.sample(timeline,id,start+charge+flight*u+epsilon,radius,outer,.16,state);
                        check(Math.abs((state.radius-saved)/epsilon-previous)<base*.0001,"radius derivative equals velocity");
                    }
                }
                double target=e*1.72*(id==6?2:1);
                check(near(previous,base*1.75),"terminal speed 1.75x base");
                check(Math.abs(integral-(target-radius*.28))<target*.00001,"numerical integral matches retained travel distance");
                double expiry=FinaleWaves.end(timeline,id,radius,outer,.16);
                FinaleWaves.sample(timeline,id,expiry,radius,outer,.16,state);
                check(near(state.radius,target)&&state.impulse==0,"unchanged radius and zero terminal radiation");
                if(id<6)check(expiry<FinaleWaves.start(timeline,id+1),"no hidden next launch");
            }
        }
        check(near(FinaleWaves.baseVelocity(timeline,3,3.2,11,.16),FinaleWaves.baseVelocity(timeline,6,3.2,11,.16)),"final double range shares equivalent ordinary base speed");
        double start=FinaleWaves.start(timeline,6),finish=FinaleWaves.end(timeline,6,3.2,11,.16);
        check(start==808,"impact start unchanged");check(finish<timeline.start(FinaleTimeline.Phase.REVERSE),"faster final wave expires on its own lifetime before reverse");
        for(int id:new int[]{0,6}) {
            var feedback=new FinaleWaveFeedback();double s=FinaleWaves.start(timeline,id),end=FinaleWaves.end(timeline,id,3.2,11,.16);
            double maxExposure=0,maxPitch=0;boolean hit=false;double hitTime=0;
            for(double t=s-1;t<end+20;t+=.05) {
                feedback.update(timeline,t,3.2,11,.16,0,0,8,8,false,false);
                if(!hit && feedback.crossed(id)){hit=true;hitTime=t;}
                maxExposure=Math.max(maxExposure,feedback.exposure);maxPitch=Math.max(maxPitch,Math.abs(feedback.pitch));
                check(Math.abs(feedback.pitch)<=1.6 && Math.abs(feedback.yaw)<=.9 && Math.abs(feedback.roll)<=.7,"bounded additive camera angles");
                if(hit && t-hitTime>4.1) check(feedback.exposure==0,"exposure gone after 0.2 seconds");
                if(hit && t-hitTime>8.1) check(feedback.chroma==0,"post-crossing chroma gone after 0.4 seconds");
            }
            check(hit && maxPitch>.1,"stationary camera receives crossing impulse");
            check(maxExposure>(id==6?.86:.57),"specified exposure peak");
            check(feedback.pitch==0 && feedback.chroma==0 && feedback.exposure==0,"feedback settles exactly to zero");
        }
        var feedback=new FinaleWaveFeedback();
        for(double t=start-1;t<finish+10;t+=5) feedback.update(timeline,t,3.2,11,.16,0,0,8,8,false,false);
        check(feedback.crossed(6),"low frame-rate segment catches crossing");
        feedback.reset();
        for(double t=start-1;t<finish+40;t+=20) feedback.update(timeline,t,3.2,11,.16,0,0,8,8,false,false);
        check(feedback.crossed(6),"one-fps crossing is detected without replaying expired brightness");
        feedback.reset();feedback.update(timeline,start+40,3.2,11,.16,0,0,8,8,false,false);
        check(!feedback.crossed(6) && feedback.exposure==0,"late join never replays crossed impulse");
        feedback.reset();feedback.update(timeline,start,3.2,11,.16,0,0,30,30,false,false);
        feedback.update(timeline,start+1,3.2,11,.16,0,0,1,1,false,false);
        check(!feedback.crossed(6),"teleport does not trigger crossing");
        feedback.reset();feedback.update(timeline,start,3.2,11,.16,0,0,8,8,false,false);
        feedback.update(timeline,start+40,3.2,11,.16,0,0,8,8,false,true);
        check(!feedback.crossed(6),"clock discontinuity does not replay crossing");
        for(double gap:new double[]{12,9,6,3,.01}) {
            feedback.reset();double t=start+FinaleWaves.chargeDuration(timeline,6)+FinaleWaves.flightDuration(timeline,6,3.2,11,.16)*.5;FinaleWaves.sample(timeline,6,t,3.2,11,.16,state);
            double distance=state.radius+gap;
            feedback.update(timeline,t,3.2,11,.16,0,0,distance,distance,false,false);
            check(Math.abs(feedback.chroma-(1-FinaleTimeline.smooth(0,12,gap)))<1.e-9,"12-block approach curve");
            double previous=feedback.chroma;
            feedback.update(timeline,t,3.2,11,.16,0,0,distance,distance,true,false);
            check(feedback.chroma==previous,"pause freezes feedback envelope");
        }
        feedback.reset();
        double pausedTick=0;
        for(double t=start-1;t<finish;t+=.05) {
            feedback.update(timeline,t,3.2,11,.16,0,0,8,8,false,false);
            if(feedback.exposure>.5) {pausedTick=t;break;}
        }
        check(pausedTick>0,"prepare active camera impulse for pause");
        double frozenPitch=feedback.pitch,frozenExposure=feedback.exposure,frozenChroma=feedback.chroma;
        for(int frame=0;frame<30;frame++) feedback.update(timeline,pausedTick,3.2,11,.16,0,0,8,8,true,false);
        check(feedback.pitch==frozenPitch && feedback.exposure==frozenExposure && feedback.chroma==frozenChroma,"pause freezes all active camera response channels");
        feedback.reset();check(feedback.pitch==0 && feedback.exposure==0 && feedback.chroma==0,"stop and resource reset clear all local feedback");
        for(int id=0;id<7;id++) {
            feedback.reset();double s=FinaleWaves.start(timeline,id),e=FinaleWaves.end(timeline,id,3.2,11,.16),distance=id==6?40:20;
            for(double t=s-1;t<=e+1;t+=.25) feedback.update(timeline,t,3.2,11,.16,0,0,distance,distance,false,false);
            check(feedback.crossed(id),"expanded range reaches camera beyond previous radius, wave "+id);
        }
        for(int summon:new int[]{4,18,120}) for(int fracture:new int[]{4,18,120}) {
            var custom=timeline.with(FinaleTimeline.Phase.SUMMON,summon).with(FinaleTimeline.Phase.FRACTURE,fracture);
            for(int id=0;id<6;id++) check(FinaleWaves.end(custom,id,3.2,11,.16)<FinaleWaves.start(custom,id+1),"custom stage durations preserve non-overlapping launches");
        }
        for(double motion:new double[]{-.1,.1}) {
            feedback.reset();boolean movingHit=false;
            for(double tick=start-1;tick<finish+2;tick+=.5) {
                double distance=18+motion*(tick-start);
                feedback.update(timeline,tick,3.2,11,.16,0,0,distance,distance,false,false);
                movingHit|=feedback.crossed(6);
            }
            check(movingHit,"moving camera crossing follows accelerated front");
        }
        var adjusted=timeline.with(FinaleTimeline.Phase.FRACTURE,4);
        check(FinaleWaves.start(adjusted,6)==adjusted.impactTicks(),"custom duration keeps impact alignment");
        check(timeline.flash(818)==1 && timeline.flash(824)==0,"white exposure timeline unchanged");
        System.out.println("PASS finale wave and camera feedback: "+checks+" assertions");
    }
}
