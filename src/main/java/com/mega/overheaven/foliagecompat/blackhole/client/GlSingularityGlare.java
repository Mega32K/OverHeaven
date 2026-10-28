package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.opengl.GL33C.*;
import org.joml.Matrix4f;
import org.joml.Vector4f;

final class GlSingularityGlare implements AutoCloseable {
    final GlTarget source = new GlTarget(GL_RGBA32F, GL_RGBA32F);
    private final Vector4f projected = new Vector4f();
    private GlProgram program;
    private boolean enabled;
    private float centerU, centerV, distance, armPixels, strength;
    private int width, height;
    int left, bottom, right, top;

    GlSingularityGlare() {}
    GlSingularityGlare(String vertex, String fragment) { program = new GlProgram(vertex, fragment); }

    static double distanceWeight(double distance, double radius) {
        double fraction = Math.max(0, Math.min(1, (distance / radius - 5) / 15));
        return fraction * fraction * (3 - 2 * fraction);
    }
    private static double smooth(double first, double last, double value) {
        double fraction = Math.max(0, Math.min(1, (value - first) / (last - first)));
        return fraction * fraction * (3 - 2 * fraction);
    }
    static double emission(int phase, double progress) {
        return switch (phase) {
            case 3 -> 1 - .83 * smooth(.35, 1, progress);
            case 4 -> .17 - .145 * smooth(0, 1, progress);
            case 5 -> .025 - .0235 * smooth(0, 1, progress);
            case 6 -> .0015 * (1 - smooth(.35, .625, progress));
            case 7 -> 0;
            default -> 1;
        };
    }
    boolean project(Matrix4f matrix, Vector4f eye, float centerX, float centerY, float centerZ,
                    double radius, double amount, double glow, double brightness,
                    int phase, double progress, int width, int height) {
        return project(matrix,eye,centerX,centerY,centerZ,radius,amount,glow,brightness,phase,progress,width,height,0);
    }
    boolean project(Matrix4f matrix, Vector4f eye, float centerX, float centerY, float centerZ,
                    double radius, double amount, double glow, double brightness,
                    int phase, double progress, int width, int height,double revealPulse) {
        enabled = false;
        if (amount <= 0 || glow <= 0 || brightness <= 0 || radius <= 0 || width <= 0 || height <= 0) return false;
        double offsetX = centerX - eye.x, offsetY = centerY - eye.y, offsetZ = centerZ - eye.z;
        double centerDistance = Math.sqrt(offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ);
        double weight = distanceWeight(centerDistance, radius);
        double pulse=Math.max(0,Math.min(1,revealPulse));
        double radiance = Math.max(weight * Math.min(1, amount),2*pulse) * glow * brightness * emission(phase, progress);
        if (radiance <= 0 || !Double.isFinite(radiance)) return false;
        matrix.transform(projected.set(centerX, centerY, centerZ, 1));
        if (projected.w <= .00001f || !Float.isFinite(projected.x) || !Float.isFinite(projected.y) || !Float.isFinite(projected.w)) return false;
        centerU = projected.x / projected.w * .5f + .5f;
        centerV = projected.y / projected.w * .5f + .5f;
        if (centerU <= 0 || centerU >= 1 || centerV <= 0 || centerV >= 1) return false;
        float edgePixels = Math.min(Math.min(centerU, 1 - centerU) * width, Math.min(centerV, 1 - centerV) * height);
        strength = (float) (radiance * smooth(0, 2, edgePixels));
        armPixels = (float) (Math.min(width, height) * .22 * (.35 + .65 * Math.sqrt(Math.max(weight,pulse))));
        distance = (float) centerDistance;
        this.width = width;
        this.height = height;
        left = Math.max(0, (int) Math.floor(centerU * width - armPixels - 2));
        right = Math.min(width, (int) Math.ceil(centerU * width + armPixels + 2));
        bottom = Math.max(0, (int) Math.floor(centerV * height - armPixels - 2));
        top = Math.min(height, (int) Math.ceil(centerV * height + armPixels + 2));
        enabled = strength > 0;
        return enabled;
    }
    void disable() { enabled = false; }
    boolean enabled() { return enabled; }
    GlProgram begin() {
        if (!enabled) throw new IllegalStateException("Inactive singularity glare");
        if (program == null) program = new GlProgram("singularity_source");
        source.resize(1, 1);
        source.bind();
        GlState.fullscreen();
        program.use();
        program.pair("GlareCenter", centerU, centerV);
        program.pair("FullSize", width, height);
        program.scalar("GlareDistance", distance);
        return program;
    }
    void bind(GlProgram composite) {
        composite.integer("GlareEnabled", enabled ? 1 : 0);
        if (!enabled) return;
        composite.texture("GlareFront", 11, source.textures[0]);
        composite.texture("GlareBack", 12, source.textures[1]);
        composite.pair("GlareCenter", centerU, centerV);
        composite.pair("GlareSize", width, height);
        composite.scalar("GlareDistance", distance);
        composite.scalar("GlareArm", armPixels);
        composite.scalar("GlareStrength", strength);
    }
    @Override public void close() {
        source.close();
        if (program != null) program.close();
        program = null;
        enabled = false;
    }
}
