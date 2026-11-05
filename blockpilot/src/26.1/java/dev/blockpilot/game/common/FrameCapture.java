package dev.blockpilot.game.common;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import dev.blockpilot.game.common.Gpu;
import dev.blockpilot.mixin.NativeImageInvoker;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ARGB;
import org.lwjgl.stb.STBImage;

/** Reading the rendered frame and its pixels, which differs between Minecraft versions. Client thread only. */
public final class FrameCapture {
    private FrameCapture() {
    }

    /**
     * The last rendered frame, read from the game's framebuffer. The game's own screenshot hands the image to a
     * callback once the GPU signals the copy done, a frame or more later; this waits for the copy instead, as reading
     * the framebuffer did before. The caller closes the image.
     */
    public static NativeImage read() {
        RenderTarget target = mainTarget();
        GpuTexture texture = target.getColorTexture();
        if (texture == null) {
            throw new IllegalStateException("the framebuffer has no colour texture yet");
        }
        int width = target.width;
        int height = target.height;
        int pixelSize = Gpu.pixelSize(texture);
        GpuDevice device = RenderSystem.getDevice();
        try (GpuBuffer buffer = device.createBuffer(() -> "Blockpilot frame",
            GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, (long) width * height * pixelSize)) {
            device.createCommandEncoder().copyTextureToBuffer(texture, buffer, 0L, () -> {
            }, 0);
            Gpu.awaitCopies();
            NativeImage image = new NativeImage(width, height, false);
            Gpu.read(buffer, pixels -> {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        // The framebuffer's rows run bottom up.
                        int abgr = pixels.getInt((x + y * width) * pixelSize);
                        image.setPixelABGR(x, height - y - 1, abgr | 0xFF000000);
                    }
                }
            });
            return image;
        }
    }

    public static RenderTarget mainTarget() {
        //? if >=26.2 {
        /*return Minecraft.getInstance().gameRenderer.mainRenderTarget();
        *///?} else {
        return Minecraft.getInstance().getMainRenderTarget();
        //?}
    }

    public static byte[] png(NativeImage image) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (WritableByteChannel channel = Channels.newChannel(bytes)) {
            if (!((NativeImageInvoker) (Object) image).blockpilot$writeToChannel(channel)) {
                throw new IOException("could not encode the PNG: " + STBImage.stbi_failure_reason());
            }
        }
        return bytes.toByteArray();
    }

    /** A pixel as NativeImage keeps it: alpha, blue, green, red from the high byte down. */
    public static int abgr(NativeImage image, int x, int y) {
        return ARGB.toABGR(image.getPixel(x, y));
    }

    /** Draws a colour whose red and blue are equal, so it reads the same as ARGB or ABGR, blending below opaque. */
    public static void draw(NativeImage image, int x, int y, int color) {
        image.setPixel(x, y, color >>> 24 == 0xFF ? color : ARGB.alphaBlend(image.getPixel(x, y), color));
    }
}
