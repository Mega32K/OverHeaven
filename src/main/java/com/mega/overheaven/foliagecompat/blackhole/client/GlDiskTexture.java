package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.opengl.GL33C.*;
import com.mega.overheaven.foliagecompat.FoliageCompat;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.system.MemoryUtil;

/** Owned, mipmapped material data. Created on demand and released with the renderer. */
final class GlDiskTexture implements AutoCloseable {
    private int texture;
    static GlDiskTexture load() {
        try (var stream = Minecraft.getInstance().getResourceManager().open(
                new ResourceLocation(FoliageCompat.MOD_ID, "textures/blackhole/disk_detail.png"))) {
            return new GlDiskTexture(stream);
        } catch (IOException exception) { throw new IllegalStateException("Missing black hole disk material", exception); }
    }
    GlDiskTexture(InputStream stream) throws IOException {
        var image = ImageIO.read(stream);
        if (image == null || image.getWidth() != 512 || image.getHeight() != 512)
            throw new IOException("Black hole disk_detail.png must be a 512x512 PNG");
        var pixels = MemoryUtil.memAlloc(512 * 512 * 4);
        int binding = glGetInteger(GL_TEXTURE_BINDING_2D), unpack = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        int alignment = glGetInteger(GL_UNPACK_ALIGNMENT), row = glGetInteger(GL_UNPACK_ROW_LENGTH);
        int skipRows = glGetInteger(GL_UNPACK_SKIP_ROWS), skipPixels = glGetInteger(GL_UNPACK_SKIP_PIXELS);
        try {
            for (int y = 0; y < 512; y++) for (int x = 0; x < 512; x++) {
                int pixel = image.getRGB(x, y);
                pixels.put((byte)(pixel >> 16)).put((byte)(pixel >> 8)).put((byte)pixel).put((byte)255);
            }
            pixels.flip();
            texture = glGenTextures(); glBindTexture(GL_TEXTURE_2D, texture);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1); glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, 0); glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 512, 512, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glGenerateMipmap(GL_TEXTURE_2D);
        } catch (RuntimeException exception) { close(); throw exception; }
        finally {
            MemoryUtil.memFree(pixels); glBindTexture(GL_TEXTURE_2D, binding); glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpack);
            glPixelStorei(GL_UNPACK_ALIGNMENT, alignment); glPixelStorei(GL_UNPACK_ROW_LENGTH, row);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, skipRows); glPixelStorei(GL_UNPACK_SKIP_PIXELS, skipPixels);
        }
    }
    int handle() { return texture; }
    @Override public void close() { if (texture != 0) glDeleteTextures(texture); texture = 0; }
}
