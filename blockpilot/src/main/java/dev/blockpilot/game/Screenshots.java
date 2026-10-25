package dev.blockpilot.game;

import com.mojang.blaze3d.platform.NativeImage;
import dev.blockpilot.game.observe.ContactSheet;
import dev.blockpilot.game.observe.FrameRect;
import dev.blockpilot.platform.ClientPlatform;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
        try (NativeImage frame = frame(width, FrameRect.ALL)) {
            return frame.asByteArray();
        }
    }

    /**
     * On the client thread: the part of the last rendered frame the crop covers, scaled to the width (never beyond
     * the part's own width, and at least 16 pixels). The caller closes it.
     */
    public static NativeImage frame(int width, FrameRect crop) {
        try (NativeImage full = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            int[] part = crop.pixels(full.getWidth(), full.getHeight());
            int targetWidth = Math.max(16, Math.min(width, part[2]));
            int targetHeight = Math.max(1, Math.round(part[3] * (targetWidth / (float) part[2])));
            NativeImage scaled = new NativeImage(targetWidth, targetHeight, false);
            full.resizeSubRectTo(part[0], part[1], part[2], part[3], scaled);
            return scaled;
        }
    }

    /** The frames as one PNG: a {@link ContactSheet} grid, each tile with its label in the top left corner. */
    public static byte[] sheet(List<NativeImage> frames, List<String> labels) throws IOException {
        ContactSheet.Layout layout = ContactSheet.layout(frames.size(), frames.get(0).getWidth(),
            frames.get(0).getHeight());
        try (NativeImage sheet = new NativeImage(layout.width(), layout.height(), true)) {
            sheet.fillRect(0, 0, layout.width(), layout.height(), ContactSheet.BACKGROUND);
            // The sheet's colours are the same read as ARGB or as NativeImage's ABGR.
            ContactSheet.Canvas canvas = (x, y, color) -> {
                if (x >= 0 && y >= 0 && x < sheet.getWidth() && y < sheet.getHeight()) {
                    if (color >>> 24 == 0xFF) {
                        sheet.setPixelRGBA(x, y, color);
                    } else {
                        sheet.blendPixel(x, y, color);
                    }
                }
            };
            for (int index = 0; index < frames.size(); index++) {
                NativeImage frame = frames.get(index);
                frame.copyRect(sheet, 0, 0, layout.x(index), layout.y(index), Math.min(frame.getWidth(),
                    layout.tileWidth()), Math.min(frame.getHeight(), layout.tileHeight()), false, false);
                ContactSheet.draw(canvas, layout.x(index) + 2, layout.y(index) + 2, labels.get(index));
            }
            return sheet.asByteArray();
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
