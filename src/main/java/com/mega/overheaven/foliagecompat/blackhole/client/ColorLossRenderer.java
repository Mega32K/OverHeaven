package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.opengl.GL33C.*;
import com.mega.overheaven.foliagecompat.colorloss.client.ColorLossClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;
import org.joml.Vector3f;

/** Frame controls and final uniform loss; the host owns layered draws and TAA history. */
public final class ColorLossRenderer implements AutoCloseable {
    public static final ColorLossRenderer INSTANCE = new ColorLossRenderer();
    private final GlTarget scene = new GlTarget(GL_RGBA16F);
    private final float[] rgb = new float[3];
    private final Vector3f residual = new Vector3f();
    private GlProgram program;
    private int vao;
    private boolean active, layered, resetHistory, resetPending, warned;
    private ColorLossRenderer() {}

    public void beginFrame(float partial) {
        var mc = Minecraft.getInstance();
        boolean previousLayered = layered;
        layered = ModList.get().getModContainerById("oculus")
                .filter(mod -> mod.getModInfo().getVersion().toString().equals("1.8.0")).isPresent()
                && OculusColorLossBridge.layered();
        active = ColorLossClient.sample(partial, rgb) && mc.level != null && mc.player != null;
        boolean requested = ColorLossClient.consumeHistoryReset();
        resetHistory = resetPending = resetPending || requested || previousLayered != layered;
        float base = Math.min(rgb[0], Math.min(rgb[1], rgb[2]));
        if (active && layered && base < 1)
            residual.set((rgb[0] - base) / (1 - base), (rgb[1] - base) / (1 - base), (rgb[2] - base) / (1 - base));
        else residual.zero();
        if (active && !layered && !warned) {
            warned = true;
            mc.gui.getChat().addMessage(Component.literal("失色系统：当前使用最终画面近似模式；材质与光照分离需要已适配的 Oculus 1.8.0 / Complementary Unbound r5.9.3。"));
        }
        // Selective-only layered effects need no private fullscreen draw or scene copy.
        active &= !layered || base > 0;
        if (!active) releaseTargets();
    }
    public Vector3f residual() { return residual; }
    public boolean resetHistory() { return resetHistory; }

    public void render(float partial) {
        // Called only after renderLevel actually ran; a loading/menu frame must not consume a reset.
        resetPending = false;
        if (!active) return;
        var target = Minecraft.getInstance().getMainRenderTarget();
        if (target.width <= 0 || target.height <= 0) return;
        try (var saved = GlState.capture()) {
            if (program == null) program = new GlProgram("color_loss");
            if (vao == 0) vao = glGenVertexArrays();
            scene.resize(target.width, target.height);
            GlState.fullscreen();
            glBindFramebuffer(GL_READ_FRAMEBUFFER, target.frameBufferId);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, scene.framebuffer);
            glBlitFramebuffer(0, 0, target.width, target.height, 0, 0, target.width, target.height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
            glBindFramebuffer(GL_FRAMEBUFFER, target.frameBufferId);
            glViewport(0, 0, target.width, target.height); glBindVertexArray(vao);
            program.use(); program.texture("Scene", 0, scene.textures[0]);
            program.vector("RgbLoss", rgb[0], rgb[1], rgb[2]); program.integer("Layered", layered ? 1 : 0);
            glDrawArrays(GL_TRIANGLES, 0, 3);
        } catch (RuntimeException error) { ColorLossClient.fail(error); }
    }
    @Override public void close() {
        active = false; residual.zero();
        releaseTargets();
    }
    private void releaseTargets() {
        if (program == null && vao == 0 && scene.framebuffer == 0) return;
        try (var saved = GlState.capture()) {
            scene.close();
            if (program != null) program.close();
            if (vao != 0) glDeleteVertexArrays(vao);
            program = null; vao = 0;
        }
    }
}
