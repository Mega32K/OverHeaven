package com.mega.overheaven.foliagecompat.finale;

import java.util.Arrays;
import java.util.Locale;

public final class FinaleTimeline {
    public enum Phase { EXPOSE, SUMMON, FRACTURE, REVERSE, UNWEAVE, EXTINGUISH, SILENCE, RETURN;
        public String id() { return name().toLowerCase(Locale.ROOT); }
        public static Phase parse(String id) { return valueOf(id.toUpperCase(Locale.ROOT)); }
    }
    public static final int[] DEFAULT_TICKS = {160,360,360,320,560,400,160,80};
    private final int[] durations;
    public FinaleTimeline(int[] ticks) {
        if (ticks.length != 8 || Arrays.stream(ticks).anyMatch(t -> t < 20 || t > 12000)) throw new IllegalArgumentException("Phase duration must be 1..600 seconds");
        durations=ticks.clone();
    }
    public static FinaleTimeline defaults() { return new FinaleTimeline(DEFAULT_TICKS); }
    public int[] durations() { return durations.clone(); }
    public int duration(Phase phase) { return durations[phase.ordinal()]; }
    public int start(Phase phase) { int result=0; for(int i=0;i<phase.ordinal();i++) result+=durations[i]; return result; }
    public int total() { return Arrays.stream(durations).sum(); }
    public FinaleTimeline with(Phase phase,double seconds) {
        if(!Double.isFinite(seconds) || seconds<1 || seconds>600) throw new IllegalArgumentException("Duration must be finite, 1..600 seconds");
        int[] copy=durations(); copy[phase.ordinal()]=(int)Math.round(seconds*20); return new FinaleTimeline(copy);
    }
    public Frame frame(double ticks) {
        double left=Math.max(0,ticks);
        for(Phase phase:Phase.values()) { int duration=duration(phase); if(left<duration) return new Frame(phase,left/duration); left-=duration; }
        return new Frame(Phase.RETURN,1);
    }
    public record Frame(Phase phase,double progress) { }
    public static double smooth(double a,double b,double value) { double t=Math.max(0,Math.min(1,(value-a)/(b-a))); return t*t*(3-2*t); }
    public double impactTicks() { return start(Phase.FRACTURE)+duration(Phase.FRACTURE)*0.8; }
    public double flash(double ticks) {
        double since=ticks-impactTicks();
        if(since<=0 || since>=16) return 0;
        if(since<10) return Math.pow(since/10,4);
        if(since<=12) return 1;
        return Math.pow((16-since)/4,4);
    }
    public double takeover(double ticks) {
        if(ticks<impactTicks()+10) return 0;
        Frame frame=frame(ticks);
        return frame.phase()==Phase.RETURN?1-smooth(0,1,frame.progress()):1;
    }
    public Exposure exposure(double ticks,double stopping) {
        double recovery=stopping<0?1:1-smooth(0,1,stopping);
        double white=flash(ticks);
        return new Exposure(takeover(ticks)*recovery,white*recovery,Math.sqrt(white)*recovery);
    }
    public record Exposure(double coverage,double white,double pulse) { }
    public static double gameGain(Frame f) {
        return switch(f.phase()) {
            case EXPOSE -> 1-0.92*smooth(0,0.06,f.progress());
            case SUMMON,FRACTURE,REVERSE -> 0.08;
            case UNWEAVE -> 0.08*(1-smooth(0,0.8,f.progress()));
            case EXTINGUISH,SILENCE -> 0;
            case RETURN -> smooth(0.05,1,f.progress());
        };
    }
    public static boolean black(Frame f) { return f.phase()==Phase.SILENCE && f.progress()>=0.625; }
}
