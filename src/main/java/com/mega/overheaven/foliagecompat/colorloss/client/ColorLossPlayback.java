package com.mega.overheaven.foliagecompat.colorloss.client;

import com.mega.overheaven.foliagecompat.colorloss.ColorLossNetwork;
import com.mega.overheaven.foliagecompat.colorloss.ColorLossProfile;
import java.util.Arrays;

/** Client clock, independently testable without starting a Minecraft client. */
public final class ColorLossPlayback {
    private ColorLossNetwork.Snapshot state;
    private int elapsed;
    private float lastPartial;
    private boolean resetHistory = true, endpointReached;

    public void accept(ColorLossNetwork.Snapshot snapshot) {
        boolean sameTransition = state != null && state.duration() == snapshot.duration()
                && Arrays.equals(state.from().values(), snapshot.from().values())
                && Arrays.equals(state.to().values(), snapshot.to().values());
        if (!sameTransition) endpointReached = false;
        if (state == null || snapshot.duration() == 0) resetHistory = true;
        state = snapshot; elapsed = snapshot.elapsed();
    }
    public void tick() { if (state != null && elapsed < state.duration()) elapsed++; }
    public boolean sample(float partial, boolean paused, float[] rgb) {
        Arrays.fill(rgb, 0);
        if (state == null) return false;
        if (!paused) lastPartial = partial;
        float progress = state.duration() == 0 ? 1 : (elapsed + lastPartial) / state.duration();
        ColorLossProfile.interpolate(state.from(), state.to(), progress, rgb);
        if (progress >= 1 && !endpointReached) { invalidateHistory(); endpointReached = true; }
        return rgb[0] > 0 || rgb[1] > 0 || rgb[2] > 0;
    }
    public void invalidateHistory() { resetHistory = true; }
    public boolean consumeHistoryReset() { boolean value = resetHistory; resetHistory = false; return value; }
    public void clear() { state = null; elapsed = 0; lastPartial = 0; endpointReached = false; invalidateHistory(); }
}
