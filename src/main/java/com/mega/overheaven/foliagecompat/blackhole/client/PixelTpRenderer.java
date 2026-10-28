package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.opengl.GL33C.*;
import com.mega.overheaven.foliagecompat.pixeltp.client.PixelTpClient;
import net.minecraft.client.Minecraft;

/** Full-resolution, private ping-pong targets; never replaces GameRenderer's post chain. */
public final class PixelTpRenderer implements AutoCloseable {
    public static final PixelTpRenderer INSTANCE = new PixelTpRenderer();
    static final int SPAN = 128;
    private final GlTarget scene = new GlTarget(GL_RGBA16F);
    private final GlTarget ping = new GlTarget(GL_RGBA32F);
    private final GlTarget pong = new GlTarget(GL_RGBA32F);
    private GlProgram sort, compose;
    private int vao;
    private PixelTpRenderer() {}

    public void render(float partial) {
        var mc = Minecraft.getInstance();
        if (!PixelTpClient.active() || mc.getConnection() == null) return;
        double amount = PixelTpClient.amount(partial);
        if (amount <= 0) return;
        var target = mc.getMainRenderTarget();
        if (target.width <= 0 || target.height <= 0) return;
        try (var state = GlState.capture()) {
            if (compose == null) compose = new GlProgram("pixel_tp_composite");
            if (vao == 0) vao = glGenVertexArrays();
            scene.resize(target.width, target.height);
            GlState.fullscreen();
            glBindFramebuffer(GL_READ_FRAMEBUFFER, target.frameBufferId);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, scene.framebuffer);
            glBlitFramebuffer(0, 0, target.width, target.height, 0, 0, target.width, target.height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
            glBindVertexArray(vao);
            int sorted = scene.textures[0];
            if (amount < 1) {
                if (sort == null) sort = new GlProgram("pixel_tp_sort");
                int paddedWidth = ((target.width + SPAN - 1) / SPAN) * SPAN;
                ping.resize(paddedWidth, target.height);
                pong.resize(paddedWidth, target.height);
                sorted = sort(scene, ping, pong, sort, amount);
            }
            glBindFramebuffer(GL_FRAMEBUFFER, target.frameBufferId);
            glViewport(0, 0, target.width, target.height);
            composite(scene.textures[0], sorted, compose, amount);
        } catch (RuntimeException error) {
            PixelTpClient.fail(error);
            return;
        }
        // The server is authorized only after a fully covered frame survived the whole render pass.
        PixelTpClient.rendered(amount);
    }
    static int sort(GlTarget scene, GlTarget ping, GlTarget pong, GlProgram program, double amount) {
        program.use();
        program.integer("SceneWidth", scene.width);
        program.scalar("Amount", amount);
        program.integer("Pass", 0);
        ping.bind();
        program.texture("Source", 0, scene.textures[0]);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        int source = ping.textures[0];
        GlTarget destination = pong;
        // Prefix maximum locates the preceding threshold boundary in each seed span.
        program.integer("Pass", 1);
        for (int step = 1; step < SPAN; step <<= 1) {
            destination.bind();
            program.texture("Source", 0, source);
            program.integer("Step", step);
            glDrawArrays(GL_TRIANGLES, 0, 3);
            source = destination.textures[0];
            destination = destination == ping ? pong : ping;
        }
        program.integer("Pass", 2);
        boolean first = true;
        for (int stage = 2; stage <= SPAN; stage <<= 1) {
            for (int step = stage >> 1; step > 0; step >>= 1) {
                destination.bind();
                program.texture("Source", 0, source);
                program.integer("Stage", stage);
                program.integer("Step", step);
                program.integer("FirstPass", first ? 1 : 0);
                glDrawArrays(GL_TRIANGLES, 0, 3);
                source = destination.textures[0];
                destination = destination == ping ? pong : ping;
                first = false;
            }
        }
        return source;
    }
    static void composite(int scene, int sorted, GlProgram program, double amount) {
        program.use();
        program.texture("Scene", 0, scene);
        program.texture("Sorted", 1, sorted);
        program.scalar("Amount", amount);
        glDrawArrays(GL_TRIANGLES, 0, 3);
    }
    @Override public void close() {
        if (vao == 0 && compose == null && sort == null && scene.framebuffer == 0 && ping.framebuffer == 0 && pong.framebuffer == 0) return;
        try (var state = GlState.capture()) {
            scene.close(); ping.close(); pong.close();
            if (compose != null) compose.close();
            if (sort != null) sort.close();
            if (vao != 0) glDeleteVertexArrays(vao);
            compose = null; sort = null; vao = 0;
        }
    }
}
