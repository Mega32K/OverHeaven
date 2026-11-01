package com.mega.overheaven.foliagecompat.illusiontp;

/** Render-driven handshake. A timer alone cannot authorize either teleport or unlocking. */
public final class IllusionTpPlayback {
    public static final double TIME_SCALE = 3.0;
    public static final double START_SPEED = 0.2;
    public static final double SPEED_RAMP_SECONDS = 1.5;
    public static final double DEPTH_FADE_SECONDS = 0.25;
    public enum Phase { COVERING, WAITING, ARRIVED, REVEALING, FINISHED }
    public enum Signal { NONE, COVERED, FINISHED }
    private Phase phase = Phase.COVERING;
    private int ticks, readyFrames;

    public Phase phase() { return phase; }
    public boolean opaque() { return phase == Phase.WAITING || phase == Phase.ARRIVED; }
    public void tick() {
        if (phase == Phase.COVERING || phase == Phase.REVEALING) ticks++;
    }
    // Glitch animation keeps its original clock; only the scan advance is eased.
    public double progress(float partial) { return (ticks + partial) * TIME_SCALE / 20.0; }
    public double radius(float partial) { return radiusAt((ticks + partial) / 20.0); }
    /** Radius reached five ticks ago: pixels behind this tail are already pure grey. */
    public double fadedRadius(float partial) {
        return radiusAt(Math.max(0.0, (ticks + partial) / 20.0 - DEPTH_FADE_SECONDS));
    }
    private static double radiusAt(double seconds) {
        double rampContribution;
        if (seconds < SPEED_RAMP_SECONDS) {
            double u = seconds / SPEED_RAMP_SECONDS;
            // Integral of smoothstep: ramp the advance rate without a jump in radius or speed.
            rampContribution = SPEED_RAMP_SECONDS * (u * u * u - 0.5 * u * u * u * u);
        } else {
            rampContribution = seconds - SPEED_RAMP_SECONDS * 0.5;
        }
        double scanProgress = TIME_SCALE * (START_SPEED * seconds + (1.0 - START_SPEED) * rampContribution);
        return scanProgress * scanProgress;
    }
    public void arrived() {
        if (phase == Phase.WAITING) { phase = Phase.ARRIVED; ticks = 0; readyFrames = 0; }
    }
    public Signal rendered(boolean waveComplete, boolean destinationReady, boolean solidFrame) {
        // Completion includes the trailing per-pixel fade, not just passage of the wave front.
        if (phase == Phase.COVERING && waveComplete && solidFrame) {
            phase = Phase.WAITING; ticks = 0;
            return Signal.COVERED;
        }
        if (phase == Phase.ARRIVED) {
            readyFrames = destinationReady ? readyFrames + 1 : 0;
            if (readyFrames >= 2) { phase = Phase.REVEALING; ticks = 0; }
        } else if (phase == Phase.REVEALING) {
            if (!destinationReady) { phase = Phase.ARRIVED; ticks = 0; readyFrames = 0; }
            else if (waveComplete) { phase = Phase.FINISHED; return Signal.FINISHED; }
        }
        return Signal.NONE;
    }
}
