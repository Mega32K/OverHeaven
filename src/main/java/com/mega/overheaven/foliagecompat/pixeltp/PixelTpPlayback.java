package com.mega.overheaven.foliagecompat.pixeltp;

/** Render-confirmed handshake: ticking alone can never authorize a teleport. */
public final class PixelTpPlayback {
    public enum Phase { ENTER, WAITING, HOLD, EXIT, FINISHED }
    private final PixelTpTiming timing;
    private Phase phase = Phase.ENTER;
    private int ticks;
    private boolean destinationRendered;

    public PixelTpPlayback(PixelTpTiming timing) { this.timing = timing; }
    public Phase phase() { return phase; }
    public double amount(float partial) {
        return switch (phase) {
            case ENTER -> timing.in() == 0 ? 1 : PixelTpTiming.smooth((ticks + partial) / timing.in());
            case WAITING, HOLD -> 1;
            case EXIT -> timing.out() == 0 ? 0 : 1 - PixelTpTiming.smooth((ticks + partial) / timing.out());
            case FINISHED -> 0;
        };
    }
    public void tick(boolean destinationReady) {
        switch (phase) {
            case ENTER -> ticks = Math.min(timing.in(), ticks + 1);
            case HOLD -> {
                if (destinationRendered && destinationReady && ++ticks >= timing.duration()) {
                    ticks = 0;
                    phase = timing.out() == 0 ? Phase.FINISHED : Phase.EXIT;
                }
            }
            case EXIT -> { if (++ticks >= timing.out()) phase = Phase.FINISHED; }
            default -> { }
        }
    }
    /** Call only after a successful, fully opaque frame has actually been drawn. */
    public boolean rendered(double amount, boolean destinationReady) {
        if (phase == Phase.ENTER && amount >= 1) {
            phase = Phase.WAITING;
            ticks = 0;
            return true;
        }
        if (phase == Phase.HOLD && destinationReady) destinationRendered = true;
        return false;
    }
    public void arrived() {
        if (phase != Phase.WAITING) return;
        phase = Phase.HOLD;
        ticks = 0;
        destinationRendered = false;
    }
}
