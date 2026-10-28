package com.mega.overheaven.foliagecompat.pixeltp;

import java.util.Random;

public final class PixelTpVerification {
    private static int checks;
    private static void expect(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        checks++;
    }
    private static void invalid(Runnable action) {
        try { action.run(); throw new AssertionError("Invalid timing accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    public static void main(String[] args) {
        invalid(() -> PixelTpTiming.seconds(Double.NaN, 1, 1));
        invalid(() -> PixelTpTiming.seconds(1, Double.POSITIVE_INFINITY, 1));
        invalid(() -> PixelTpTiming.seconds(1, 1, -0.1));
        invalid(() -> PixelTpTiming.seconds(60.1, 1, 1));
        invalid(() -> new PixelTpTiming(0, 1201, 0));
        expect(PixelTpTiming.seconds(0.001, 0, 60).equals(new PixelTpTiming(1, 0, 1200)), "seconds, tick rounding and limits");
        var random = new Random(3999);
        for (int scenario = 0; scenario < 128; scenario++) {
            var timing = new PixelTpTiming(scenario < 4 ? 0 : random.nextInt(31), random.nextInt(31), scenario < 4 ? 0 : random.nextInt(31));
            var playback = new PixelTpPlayback(timing);
            double previous = 0;
            // Many ticks without a rendered frame must never produce a COVERED acknowledgement.
            for (int tick = 0; tick < timing.in() + 40; tick++) {
                double amount = playback.amount(0);
                expect(amount >= previous && amount >= 0 && amount <= 1, "monotonic bounded entrance");
                previous = amount;
                playback.tick(false);
            }
            expect(playback.phase() == PixelTpPlayback.Phase.ENTER, "ticks cannot authorize teleport");
            expect(playback.rendered(1, false), "first opaque frame acknowledges exactly once");
            for (int delay = 0; delay < 80; delay++) {
                playback.tick(false);
                expect(!playback.rendered(1, false) && playback.amount(0.9f) == 1, "latency keeps full coverage without duplicate ACK");
            }
            playback.arrived();
            for (int tick = 0; tick < 80; tick++) {
                playback.tick(false);
                playback.rendered(1, false);
                expect(playback.phase() == PixelTpPlayback.Phase.HOLD, "unloaded destination cannot reveal");
            }
            for (int tick = 0; tick < 80; tick++) playback.tick(true);
            expect(playback.phase() == PixelTpPlayback.Phase.HOLD, "chunk availability alone cannot replace a rendered destination frame");
            playback.rendered(1, true);
            for (int tick = 0; tick < Math.max(1, timing.duration()); tick++) {
                expect(playback.amount(0) == 1, "configured hold is opaque");
                playback.tick(true);
            }
            expect(playback.phase() == (timing.out() == 0 ? PixelTpPlayback.Phase.FINISHED : PixelTpPlayback.Phase.EXIT), "hold duration respected");
            previous = 1;
            for (int tick = 0; tick < timing.out(); tick++) {
                double amount = playback.amount(0);
                expect(amount <= previous && amount >= 0 && amount <= 1, "monotonic bounded exit");
                previous = amount;
                playback.tick(true);
            }
            expect(playback.phase() == PixelTpPlayback.Phase.FINISHED && playback.amount(0) == 0, "no residual overlay");
        }
        var outOfOrder = new PixelTpPlayback(new PixelTpTiming(20, 20, 20));
        outOfOrder.arrived();
        expect(outOfOrder.phase() == PixelTpPlayback.Phase.ENTER, "early ARRIVED cannot skip entrance");
        expect(!outOfOrder.rendered(0.99, true), "partial coverage cannot authorize teleport");
        System.out.println("PASS " + checks + " pixelTp timing/handshake checks; no Minecraft runtime exercised.");
    }
}
