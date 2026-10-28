package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;

/** Small shader smoke check and visual fixture, not a Minecraft integration test. */
public final class PixelTpGpuVerification {
    private static void image(GlTarget target, Path file, double amount, float[] input) throws Exception {
        target.bind();
        var buffer = BufferUtils.createFloatBuffer(target.width * target.height * 4);
        glReadPixels(0, 0, target.width, target.height, GL_RGBA, GL_FLOAT, buffer);
        var image = new BufferedImage(target.width, target.height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < target.height; y++) for (int x = 0; x < target.width; x++) {
            int color = 0, at = (y * target.width + x) * 4;
            for (int c = 0; c < 3; c++) {
                float value = buffer.get(at + c);
                if (!Float.isFinite(value)) throw new AssertionError("Nonfinite output");
                if (amount == 0 && Math.abs(value - input[at + c]) > 0.001) throw new AssertionError("Zero intensity changed the scene");
                if (amount == 1 && value != 0) throw new AssertionError("Incomplete teleport cover");
                color |= Math.max(0, Math.min(255, Math.round(value * 255))) << (16 - c * 8);
            }
            image.setRGB(x, target.height - 1 - y, color);
        }
        ImageIO.write(image, "png", file.toFile());
    }
    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[0]), output = Path.of(args[1]);
        Files.createDirectories(output);
        if (!glfwInit()) throw new IllegalStateException("GLFW initialization failed");
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        long window = glfwCreateWindow(64, 64, "pixelTp shader preview", 0, 0);
        if (window == 0) throw new IllegalStateException("GL context creation failed");
        glfwMakeContextCurrent(window); GL.createCapabilities();
        int vao = glGenVertexArrays(); glBindVertexArray(vao);
        String gpu = glGetString(GL_RENDERER);
        String vertex = Files.readString(source.resolve("fullscreen.vsh"));
        try (var sort = new GlProgram(vertex, Files.readString(source.resolve("pixel_tp_sort.fsh")));
             var compose = new GlProgram(vertex, Files.readString(source.resolve("pixel_tp_composite.fsh")));
             var scene = new GlTarget(GL_RGBA16F); var ping = new GlTarget(GL_RGBA32F);
             var pong = new GlTarget(GL_RGBA32F); var result = new GlTarget(GL_RGBA16F)) {
            GlState.fullscreen();
            // Deliberately includes one partial seed span at the right edge.
            int width = 641, height = 360, padded = ((width + 127) / 128) * 128;
            scene.resize(width, height); result.resize(width, height);
            ping.resize(padded, height); pong.resize(padded, height);
            float[] fixture = new float[width * height * 4];
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int at = (y * width + x) * 4;
                float r, g, b;
                if (y < 128 + 30 * Math.sin(x * 0.015)) { r = 0.17f; g = 0.32f; b = 0.22f; }
                else { r = 0.22f + y / 1600f; g = 0.34f + y / 1200f; b = 0.53f + y / 1800f; }
                if (x > 185 && x < 455 && y < 255 && y > 72) {
                    boolean windowPixel = x % 48 < 27 && y % 52 < 32;
                    r = windowPixel ? 0.98f : 0.38f; g = windowPixel ? 0.72f : 0.22f; b = windowPixel ? 0.28f : 0.19f;
                }
                fixture[at] = r; fixture[at + 1] = g; fixture[at + 2] = b; fixture[at + 3] = 1;
            }
            glBindTexture(GL_TEXTURE_2D, scene.textures[0]);
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA, GL_FLOAT,
                    BufferUtils.createFloatBuffer(fixture.length).put(fixture).flip());
            for (double amount : new double[]{0, 0.2, 0.45, 0.65, 0.85, 1}) {
                int sorted = PixelTpRenderer.sort(scene, ping, pong, sort, amount);
                result.bind(); PixelTpRenderer.composite(scene.textures[0], sorted, compose, amount);
                image(result, output.resolve(String.format(Locale.ROOT, "amount-%.2f.png", amount)), amount, fixture);
            }
            if (glGetError() != GL_NO_ERROR) throw new AssertionError("OpenGL error");
        } finally { glDeleteVertexArrays(vao); glfwDestroyWindow(window); glfwTerminate(); }
        String report = "GPU: " + gpu + "\nPASS GLSL compilation/linking, six frames, finite output, original/black endpoints, partial seed span.\n"
                + "Synthetic scene only; no Minecraft or host shader compatibility validation.\n";
        Files.writeString(output.resolve("report.txt"), report);
        System.out.println(report);
    }
}
