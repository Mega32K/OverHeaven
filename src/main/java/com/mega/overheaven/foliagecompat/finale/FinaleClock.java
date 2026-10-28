package com.mega.overheaven.foliagecompat.finale;

/** Mutable server cursor, isolated from world access so pause/stop ordering can be exercised. */
public final class FinaleClock {
    private final FinaleTimeline timeline;
    private int elapsed,stopping=-1;
    private boolean paused;
    public FinaleClock(FinaleTimeline timeline) { this.timeline=timeline; }
    public int elapsed() { return elapsed; }
    public int stopping() { return stopping; }
    public boolean paused() { return paused; }
    public void pause(boolean value) { if(stopping>=0) throw new IllegalArgumentException("Finale is returning"); paused=value; }
    public void stop() { if(stopping<0) { stopping=0; paused=false; } }
    public void tick() { if(stopping>=0) stopping=Math.min(40,stopping+1); else if(!paused) elapsed=Math.min(timeline.total(),elapsed+1); }
    public boolean finished() { return stopping>=40 || elapsed>=timeline.total(); }
}
