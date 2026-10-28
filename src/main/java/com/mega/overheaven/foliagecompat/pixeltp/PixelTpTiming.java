package com.mega.overheaven.foliagecompat.pixeltp;

/** Durations are stored in game ticks; command input is in seconds. */
public record PixelTpTiming(int in, int duration, int out) {
    public static final int MAX_TICKS = 1200;
    public PixelTpTiming {
        if (in < 0 || duration < 0 || out < 0 || in > MAX_TICKS || duration > MAX_TICKS || out > MAX_TICKS)
            throw new IllegalArgumentException("pixelTp durations must be between 0 and 60 seconds");
    }
    public static PixelTpTiming seconds(double in, double duration, double out) {
        return new PixelTpTiming(ticks(in), ticks(duration), ticks(out));
    }
    private static int ticks(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 60)
            throw new IllegalArgumentException("Invalid pixelTp duration");
        return value == 0 ? 0 : Math.max(1, (int) Math.round(value * 20));
    }
    public int total() { return in + duration + out; }
    public static double smooth(double value) {
        double x = Math.max(0, Math.min(1, value));
        return x * x * (3 - 2 * x);
    }
}
