package dev.blockpilot.game;

import com.mojang.blaze3d.platform.NativeImage;
import dev.blockpilot.platform.ClientPlatform;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * Captures the last rendered frame from the game's own framebuffer (not the screen), so it works while the window
 * is in the background or covered, scaled down to the requested width and encoded as PNG by the game's encoder.
 */
public final class Screenshots {
    private static final DateTimeFormatter NAMES = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss.SSS");

    private Screenshots() {
    }

    /** From any thread: the PNG of the next frame boundary. */
    public static CompletableFuture<byte[]> capture(int width) {
        CompletableFuture<byte[]> result = new CompletableFuture<>();
        Minecraft.getInstance().execute(() -> {
            try {
                result.complete(captureNow(width));
            } catch (IOException | RuntimeException e) {
                result.completeExceptionally(e);
            }
        });
        return result;
    }

    /** On the client thread: the last rendered frame as PNG. */
    public static byte[] captureNow(int width) throws IOException {
        Minecraft minecraft = Minecraft.getInstance();
        try (NativeImage full = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            int targetWidth = Math.max(16, Math.min(width, full.getWidth()));
            int targetHeight = Math.max(1, Math.round(full.getHeight() * (targetWidth / (float) full.getWidth())));
            if (targetWidth == full.getWidth()) {
                return full.asByteArray();
            }
            try (NativeImage scaled = new NativeImage(targetWidth, targetHeight, false)) {
                full.resizeSubRectTo(0, 0, full.getWidth(), full.getHeight(), scaled);
                return scaled.asByteArray();
            }
        }
    }

    /** Saves a capture under {@code <gameDir>/blockpilot/screenshots/} and returns the file. */
    public static Path save(byte[] png, String suffix) throws IOException {
        Path directory = ClientPlatform.gameDir().resolve("blockpilot").resolve("screenshots");
        Files.createDirectories(directory);
        Path file = directory.resolve(LocalDateTime.now().format(NAMES) + suffix + ".png");
        Files.write(file, png);
        return file.toAbsolutePath();
    }
}
