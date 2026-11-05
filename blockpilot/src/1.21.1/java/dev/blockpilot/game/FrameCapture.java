package dev.blockpilot.game;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Reading the rendered frame and its pixels, which differs between Minecraft versions. Client thread only. */
public final class FrameCapture {
    private FrameCapture() {
    }

    /** The last rendered frame, read from the game's framebuffer. The caller closes it. */
    public static NativeImage read() {
        return Screenshot.takeScreenshot(mainTarget());
    }

    public static RenderTarget mainTarget() {
        return Minecraft.getInstance().getMainRenderTarget();
    }

    public static byte[] png(NativeImage image) throws IOException {
        return image.asByteArray();
    }

    /** A pixel as NativeImage keeps it: alpha, blue, green, red from the high byte down. */
    public static int abgr(NativeImage image, int x, int y) {
        return image.getPixelRGBA(x, y);
    }

    /** Draws a colour whose red and blue are equal, so it reads the same as ARGB or ABGR, blending below opaque. */
    public static void draw(NativeImage image, int x, int y, int color) {
        if (color >>> 24 == 0xFF) {
            image.setPixelRGBA(x, y, color);
        } else {
            image.blendPixel(x, y, color);
        }
    }
}
