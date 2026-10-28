package com.mega.overheaven.foliagecompat;

import com.mega.overheaven.foliagecompat.finale.*;

public final class FinaleFeedbackVerification {
    private static int checks;
    private static void check(boolean value,String description) { if(!value) throw new AssertionError(description);checks++; }
    private static boolean near(double a,double b) { return Math.abs(a-b)<1.e-6; }
    private static double legacy(double radius,double outer,double thickness,double age) {
        double extent=FinaleWaves.extent(radius,outer,thickness),launch=Math.max(0,Math.min(1,(age-.09)/.91));
        double travel=.4*(1-Math.exp(-launch*6))/(1-Math.exp(-6))+.6*launch;
        double charge=radius*(.44-.16*FinaleTimeline.smooth(0,.09,age));
        return charge+(extent*.86-charge)*travel;
    }
    public static void main(String[] args) {
        var timeline=FinaleTimeline.defaults();var state=new FinaleWaves.State();
        for(double radius:new double[]{.1,3.2,20,80}) for(double outer:new double[]{radius+2,radius+12,128}) {
            if(outer<=radius) continue;
            for(int id=0;id<7;id++) for(int i=0;i<=100;i++) {
                double age=i/100.,start=FinaleWaves.start(timeline,id),duration=FinaleWaves.duration(timeline,id);
                FinaleWaves.sample(timeline,id,start+duration*age,radius,outer,.16,state);
                check(near(state.radius,legacy(radius,outer,.16,age)),"same launch curve at 1.5x current speed, including final common range");
                check(state.support>=state.radius+state.extent*.12,"wave and fragments within culling support");
            }
            for(int id=0;id<7;id++) {
                double s=FinaleWaves.start(timeline,id),d=FinaleWaves.duration(timeline,id);
                double oldDuration=timeline.duration(id<3?FinaleTimeline.Phase.SUMMON:FinaleTimeline.Phase.FRACTURE)
                    *new double[]{.25,.22,.19,.20,.18,.16,.20}[id]*.5;
                check(near(d*1.5,oldDuration),"propagation speed exactly 1.5x current, not 3x");
                double previousTerminal=FinaleWaves.extent(radius,outer,.16)*.86*(id==6?2:1);
                double expiry=FinaleWaves.end(timeline,id,radius,outer,.16);
                FinaleWaves.sample(timeline,id,expiry,radius,outer,.16,state);
                check(near(state.radius,2*previousTerminal),"all seven terminal radii double");
                check(state.impulse==0,"every wave expires smoothly");
                if(id<6) check(expiry<FinaleWaves.start(timeline,id+1),"extended waves never hide the next launch");
                FinaleWaves.sample(timeline,id,s+d-.001,radius,outer,.16,state);double l=state.radius;
                FinaleWaves.sample(timeline,id,s+d,radius,outer,.16,state);double m=state.radius;
                FinaleWaves.sample(timeline,id,s+d+.001,radius,outer,.16,state);
                check(Math.abs((m-l)-(state.radius-m))<radius*1.e-7,"every extension has continuous velocity");
                check(near((state.radius-m)/.001,1.5*FinaleWaves.endVelocity(radius,FinaleWaves.extent(radius,outer,.16),oldDuration)),"extended travel keeps precisely 1.5x previous terminal speed");
            }
            double finish=FinaleWaves.end(timeline,6,radius,outer,.16);
            FinaleWaves.sample(timeline,6,finish,radius,outer,.16,state);
            check(near(state.radius,2*FinaleWaves.terminal(radius,outer,.16)),"final wave exactly double terminal radius");
            check(state.impulse==0,"expiry radiance zero");
        }
        double start=FinaleWaves.start(timeline,6),normalEnd=start+FinaleWaves.duration(timeline,6),finish=FinaleWaves.end(timeline,6,3.2,11,.16);
        check(start==808,"impact start unchanged");check(finish>timeline.start(FinaleTimeline.Phase.REVERSE),"final wave crosses into reverse");
        FinaleWaves.sample(timeline,6,normalEnd-.001,3.2,11,.16,state);double before=state.radius;
        FinaleWaves.sample(timeline,6,normalEnd,3.2,11,.16,state);double at=state.radius;
        FinaleWaves.sample(timeline,6,normalEnd+.001,3.2,11,.16,state);
        check(Math.abs((at-before)-(state.radius-at))<1.e-7,"extension preserves terminal speed");
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
            feedback.reset();double t=start+30;FinaleWaves.sample(timeline,6,t,3.2,11,.16,state);
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
        var adjusted=timeline.with(FinaleTimeline.Phase.FRACTURE,4);
        check(FinaleWaves.start(adjusted,6)==adjusted.impactTicks(),"custom duration keeps impact alignment");
        check(timeline.flash(818)==1 && timeline.flash(824)==0,"white exposure timeline unchanged");
        System.out.println("PASS finale wave and camera feedback: "+checks+" assertions");
    }
}
