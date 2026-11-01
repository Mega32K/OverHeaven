package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.opengl.GL33C.*;
import com.mega.overheaven.foliagecompat.illusiontp.client.IllusionTpClient;
import com.mega.overheaven.foliagecompat.illusiontp.IllusionTpPlayback;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Private scene-depth/hand copies, composited after host post effects and before HUD. */
public final class IllusionTpRenderer implements AutoCloseable {
    public static final IllusionTpRenderer INSTANCE = new IllusionTpRenderer();
    private static final float[] COVERED_COLOR = {36.0F / 255, 36.0F / 255, 36.0F / 255, 1};
    private final GlTarget scene = new GlTarget(GL_RGBA16F);
    private final GlTarget depth = new GlTarget(GL_R32F);
    private final GlTarget beforeHand = new GlTarget(GL_R32F);
    private GlTarget hand = new GlTarget(GL_R32F), handScratch = new GlTarget(GL_R32F);
    private final Matrix4f inverseVP = new Matrix4f();
    private final Vector4f eye = new Vector4f(), point = new Vector4f();
    private GlProgram capture, compose;
    private int vao, depthSampler, oculusDepth, coverageQuery;
    private int coveragePhase = -1;
    private boolean coveragePending, coverageComplete;
    private boolean matrixReady, worldCaptured, handPresent, handPending;
    private float horizonDistance, farthestDistance;

    private IllusionTpRenderer() {}
    public void beginFrame() {
        matrixReady = false; worldCaptured = false; handPresent = false; handPending = false; oculusDepth = 0;
    }
    public void camera(Matrix4f view, Matrix4f projection) {
        if (!IllusionTpClient.active() || OptionalShaders.shadow()) return;
        inverseVP.set(projection).mul(view).invert();
        inverseVP.transform(eye.set(0, 0, 1, 0));
        eye.div(eye.w);
        horizonDistance = distance(0, 0);
        farthestDistance = horizonDistance * 2;
        for (int x = -1; x <= 1; x += 2) for (int y = -1; y <= 1; y += 2)
            farthestDistance = Math.max(farthestDistance, distance(x, y));
        matrixReady = inverseVP.isFinite() && Float.isFinite(farthestDistance) && horizonDistance > 0;
    }
    private float distance(float x, float y) {
        inverseVP.transform(point.set(x, y, 1, 1));
        point.div(point.w);
        float dx = point.x - eye.x, dy = point.y - eye.y, dz = point.z - eye.z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz) / 16;
    }
    public void beginOculus(int texture) {
        if (IllusionTpClient.active() && !OptionalShaders.shadow()) oculusDepth = texture;
    }
    public void oculusWorld(int texture, Matrix4f view, Matrix4f projection) {
        if (!IllusionTpClient.active() || OptionalShaders.shadow()) return;
        camera(view, projection);
        captureWorld(texture);
    }
    public void vanillaWorld() {
        if (IllusionTpClient.active() && !OptionalShaders.active())
            captureWorld(Minecraft.getInstance().getMainRenderTarget().getDepthTextureId());
    }
    private void captureWorld(int texture) {
        if (!matrixReady || texture <= 0) return;
        try (var saved = GlState.capture()) {
            prepare();
            copyDepth(texture, depth);
            worldCaptured = true;
        } catch (RuntimeException error) { IllusionTpClient.fail(error); }
    }
    public void beforeOculusHand() {
        handPending = false;
        if (!IllusionTpClient.active() || oculusDepth <= 0 || OptionalShaders.shadow()) return;
        try (var saved = GlState.capture()) {
            prepare();
            copyDepth(oculusDepth, beforeHand);
            handPending = true;
        } catch (RuntimeException error) { IllusionTpClient.fail(error); }
    }
    public void afterOculusHand() {
        if (!handPending || !IllusionTpClient.active()) return;
        handPending = false;
        captureHand(oculusDepth, 1);
    }
    public void vanillaHand() {
        if (IllusionTpClient.active() && worldCaptured && !OptionalShaders.active())
            captureHand(Minecraft.getInstance().getMainRenderTarget().getDepthTextureId(), 2);
    }
    private void captureHand(int texture, int mode) {
        if (texture <= 0) return;
        try (var saved = GlState.capture()) {
            prepare();
            int width = Minecraft.getInstance().getMainRenderTarget().width;
            int height = Minecraft.getInstance().getMainRenderTarget().height;
            hand.resize(width, height); handScratch.resize(width, height);
            handScratch.bind();
            capture.use(); capture.integer("Mode", mode); capture.integer("HasMask", handPresent ? 1 : 0);
            bindDepth("Source", 0, texture);
            capture.texture("BeforeHand", 1, mode == 1 ? beforeHand.textures[0] : texture);
            capture.texture("PreviousMask", 2, hand.textures[0]);
            glDrawArrays(GL_TRIANGLES, 0, 3);
            GlTarget previous = hand; hand = handScratch; handScratch = previous;
            handPresent = true;
        } catch (RuntimeException error) { IllusionTpClient.fail(error); }
    }
    private void prepare() {
        if (capture == null) capture = new GlProgram("illusion_tp_capture");
        if (vao == 0) vao = glGenVertexArrays();
        if (depthSampler == 0) {
            depthSampler = glGenSamplers();
            glSamplerParameteri(depthSampler, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glSamplerParameteri(depthSampler, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glSamplerParameteri(depthSampler, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glSamplerParameteri(depthSampler, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glSamplerParameteri(depthSampler, GL_TEXTURE_COMPARE_MODE, GL_NONE);
        }
        GlState.fullscreen(); glBindVertexArray(vao);
    }
    private void bindDepth(String name, int unit, int texture) {
        capture.texture(name, unit, texture);
        glBindSampler(unit, depthSampler);
    }
    private void copyDepth(int source, GlTarget destination) {
        var main = Minecraft.getInstance().getMainRenderTarget();
        destination.resize(main.width, main.height); destination.bind();
        capture.use(); capture.integer("Mode", 0);
        bindDepth("Source", 0, source);
        // Unused samplers must not retain an attachment of this copy destination.
        capture.texture("BeforeHand", 1, source);
        capture.texture("PreviousMask", 2, source);
        glDrawArrays(GL_TRIANGLES, 0, 3);
    }
    public void render(float partial) {
        var mc = Minecraft.getInstance();
        if (!IllusionTpClient.active() || mc.getConnection() == null) return;
        boolean reveal = IllusionTpClient.revealing();
        boolean forceCovered = IllusionTpClient.opaque()
                || reveal && (!IllusionTpClient.destinationReady() || !worldCaptured);
        boolean scanning = !forceCovered;
        if (!worldCaptured && !forceCovered) return;
        var target = mc.getMainRenderTarget();
        if (target.width <= 0 || target.height <= 0) return;
        if (!worldCaptured) {
            // Minecraft.setLevel forces a render-only frame before the destination world/camera exists.
            // This path needs no old-world textures, projection, hand mask, or shader resources.
            try (var saved = GlState.capture()) {
                GlState.fullscreen();
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, target.frameBufferId);
                glClearBufferfv(GL_COLOR, 0, COVERED_COLOR);
                coveragePhase = -1; coveragePending = false; coverageComplete = false;
            } catch (RuntimeException error) {
                IllusionTpClient.fail(error);
                return;
            }
            IllusionTpClient.rendered(false, false, true);
            return;
        }
        double fadedRadius = IllusionTpClient.fadedRadius(partial);
        // Reveal reverses the local sequence: the scene-restoring edge follows the depth fade-in.
        double radius = reveal ? fadedRadius : IllusionTpClient.radius(partial);
        double width = 24.0 * Math.hypot(1.0 / target.width, 1.0 / target.height);
        boolean complete = worldCaptured && fadedRadius >= farthestDistance + width * 4.5 + 2.0;
        try (var saved = GlState.capture()) {
            prepare();
            if (!scanning) {
                // Discard results from the old wave, including a reveal interrupted by lost readiness.
                coveragePhase = -1; coveragePending = false; coverageComplete = false;
            } else {
                int phase = reveal ? 1 : 0;
                if (phase != coveragePhase) {
                    coveragePhase = phase; coveragePending = false; coverageComplete = false;
                }
                // Never block the render thread waiting for GPU readback. A completed all-covered frame latches coverage.
                if (coveragePending && glGetQueryObjecti(coverageQuery, GL_QUERY_RESULT_AVAILABLE) != 0) {
                    coverageComplete = glGetQueryObjecti(coverageQuery, GL_QUERY_RESULT) == 0;
                    coveragePending = false;
                }
                complete |= coverageComplete;
            }
            if (compose == null) compose = new GlProgram("illusion_tp_composite");
            scene.resize(target.width, target.height);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, target.frameBufferId);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, scene.framebuffer);
            glBlitFramebuffer(0, 0, target.width, target.height, 0, 0, target.width, target.height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
            glBindFramebuffer(GL_FRAMEBUFFER, target.frameBufferId);
            glViewport(0, 0, target.width, target.height);
            compose.use();
            compose.texture("Scene", 0, scene.textures[0]);
            compose.texture("SceneDepth", 1, depth.textures[0]);
            compose.texture("HandMask", 2, hand.textures[0]);
            compose.integer("HasHand", handPresent ? 1 : 0);
            compose.integer("Reveal", reveal ? 1 : 0);
            compose.integer("ForceCovered", forceCovered ? 1 : 0);
            compose.integer("Complete", complete ? 1 : 0);
            compose.vector4("WaveTiming", IllusionTpPlayback.TIME_SCALE, IllusionTpPlayback.START_SPEED,
                    IllusionTpPlayback.SPEED_RAMP_SECONDS, IllusionTpPlayback.DEPTH_FADE_SECONDS);
            compose.integer("CoverageOnly", 0);
            compose.matrix("InverseVP", inverseVP);
            compose.vector("Eye", eye.x, eye.y, eye.z);
            compose.pair("PixelSize", 1.0f / target.width, 1.0f / target.height);
            compose.scalar("Horizon", horizonDistance);
            compose.scalar("Radius", radius);
            compose.scalar("Progress", IllusionTpClient.progress(partial));
            glDrawArrays(GL_TRIANGLES, 0, 3);
            if (scanning && !complete && !coveragePending
                    && glGetQueryi(GL_ANY_SAMPLES_PASSED, GL_CURRENT_QUERY) == 0) {
                if (coverageQuery == 0) coverageQuery = glGenQueries();
                compose.integer("CoverageOnly", 1);
                glColorMask(false, false, false, false);
                glBeginQuery(GL_ANY_SAMPLES_PASSED, coverageQuery);
                glDrawArrays(GL_TRIANGLES, 0, 3);
                glEndQuery(GL_ANY_SAMPLES_PASSED);
                coveragePending = true;
            }
        } catch (RuntimeException error) {
            IllusionTpClient.fail(error);
            return;
        }
        // Confirm only after the final display-space composition, outside the GL state scope.
        IllusionTpClient.rendered(complete, worldCaptured, complete && !reveal && !forceCovered);
    }
    @Override public void close() {
        if (vao == 0 && capture == null && compose == null) { beginFrame(); return; }
        try (var saved = GlState.capture()) {
            scene.close(); depth.close(); beforeHand.close(); hand.close(); handScratch.close();
            if (capture != null) capture.close();
            if (compose != null) compose.close();
            if (vao != 0) glDeleteVertexArrays(vao);
            if (depthSampler != 0) glDeleteSamplers(depthSampler);
            if (coverageQuery != 0) glDeleteQueries(coverageQuery);
            capture = null; compose = null; vao = 0; depthSampler = 0; coverageQuery = 0;
            coveragePhase = -1; coveragePending = false; coverageComplete = false;
        }
        beginFrame();
    }
}
