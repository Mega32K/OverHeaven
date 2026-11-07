package com.mega.overheaven.foliagecompat.colorloss;

/** Server-world tick clock survives saving, dimensional travel and player reconnects. */
public record ColorLossTransition(ColorLossProfile from, ColorLossProfile to, long start, int duration) {
    public static final int MAX_DURATION = 20 * 60 * 60;
    public ColorLossTransition {
        if (duration < 0 || duration > MAX_DURATION) throw new IllegalArgumentException("渐变时长须为 0～3600 秒");
    }
    public static ColorLossTransition steady(ColorLossProfile profile) { return new ColorLossTransition(profile, profile, 0, 0); }
    public int elapsed(long now) { return (int) Math.max(0, Math.min((long) duration, now - start)); }
    public boolean running(long now) { return elapsed(now) < duration; }
    public ColorLossProfile current(long now) {
        return duration == 0 || !running(now) ? to : ColorLossProfile.interpolate(from, to, elapsed(now) / (float) duration);
    }
    public ColorLossTransition change(long now, ColorLossProfile target, int ticks) {
        return new ColorLossTransition(current(now), target, now, ticks);
    }
}
