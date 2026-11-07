package com.mega.overheaven.foliagecompat.colorloss;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/** Independent R/G/B color-loss controls, not multipliers of framebuffer channels. */
public final class ColorLossProfile {
    public static final int SAMPLES = 3;
    private static final Set<String> COLORS = Set.of("R", "G", "B");
    private final float[] loss;

    public ColorLossProfile(float[] values) {
        if (values.length != SAMPLES) throw new IllegalArgumentException("失色设置必须包含 R、G、B 三个值");
        for (float value : values)
            if (!Float.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("失色强度须为 0～1");
        loss = values.clone();
    }
    public static ColorLossProfile uniform(float amount) {
        float[] values = new float[SAMPLES]; Arrays.fill(values, amount);
        return new ColorLossProfile(values);
    }
    public float[] values() { return loss.clone(); }
    public float at(int index) { return loss[index]; }
    public static Set<String> names() { return COLORS; }
    public static int channel(String color) {
        return switch (color.toUpperCase(Locale.ROOT)) {
            case "R", "RED" -> 0;
            case "G", "GREEN" -> 1;
            case "B", "BLUE" -> 2;
            default -> throw new IllegalArgumentException("颜色仅支持 R、G、B（兼容 red、green、blue）");
        };
    }
    public ColorLossProfile color(int channel, float amount) {
        if (channel < 0 || channel >= SAMPLES) throw new IllegalArgumentException("无效的 RGB 通道");
        float[] next = loss.clone();
        next[channel] = amount;
        return new ColorLossProfile(next);
    }
    public static ColorLossProfile interpolate(ColorLossProfile from, ColorLossProfile to, float progress) {
        float[] values = new float[SAMPLES]; interpolate(from, to, progress, values);
        return new ColorLossProfile(values);
    }
    public static void interpolate(ColorLossProfile from, ColorLossProfile to, float progress, float[] output) {
        float t = Math.max(0, Math.min(1, progress)); t = t * t * (3 - 2 * t);
        for (int i = 0; i < SAMPLES; i++) output[i] = from.loss[i] + (to.loss[i] - from.loss[i]) * t;
    }
    public float min() { float result = 1; for (float value : loss) result = Math.min(result, value); return result; }
    public float max() { float result = 0; for (float value : loss) result = Math.max(result, value); return result; }
}
