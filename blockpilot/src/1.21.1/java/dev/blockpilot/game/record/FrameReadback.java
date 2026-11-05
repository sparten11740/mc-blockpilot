package dev.blockpilot.game.record;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.blockpilot.game.common.FrameCapture;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

/**
 * Finished frames read back from the GPU at the recording's size: blitted with linear filtering into a framebuffer of
 * that size, read into a pixel buffer object behind a fence, and mapped once the fence has signalled, a frame or two
 * later. A frame more than twice the size is halved in steps first, so the linear filter doesn't skip pixels. The
 * game doesn't cache framebuffer or buffer bindings, so they are put back as they were. Client thread only.
 */
final class FrameReadback implements AutoCloseable {
    private static final int SLOTS = 3;
    private static final long WAIT_NANOS = 1_000_000_000L;

    private record Level(int framebuffer, int renderbuffer, int width, int height) {
        static Level create(int width, int height) {
            int framebuffer = GL30.glGenFramebuffers();
            int renderbuffer = GL30.glGenRenderbuffers();
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, renderbuffer);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL11.GL_RGBA8, width, height);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);
            int bound = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
            GL30.glFramebufferRenderbuffer(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_RENDERBUFFER,
                renderbuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, bound);
            return new Level(framebuffer, renderbuffer, width, height);
        }

        void close() {
            GL30.glDeleteFramebuffers(framebuffer);
            GL30.glDeleteRenderbuffers(renderbuffer);
        }
    }

    private final int width;
    private final int height;
    private final Level target;
    private final int[] buffers = new int[SLOTS];
    private final long[] fences = new long[SLOTS];
    private final long[] stamps = new long[SLOTS];
    private final List<Level> halves = new ArrayList<>();
    private int sourceWidth;
    private int sourceHeight;
    private int next;
    private int pending;
    private int waits;

    FrameReadback(int width, int height) {
        this.width = width;
        this.height = height;
        target = Level.create(width, height);
        int bound = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        for (int slot = 0; slot < SLOTS; slot++) {
            buffers[slot] = GL15.glGenBuffers();
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, buffers[slot]);
            GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, (long) width * height * 4, GL15.GL_STREAM_READ);
        }
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, bound);
    }

    /** Copies the frame just finished, stamped {@code nanos}, and hands the copies already done to the sink. */
    void capture(long nanos, FrameSink sink) {
        RenderTarget main = FrameCapture.mainTarget();
        if (main.frameBufferId < 0) {
            return;
        }
        if (pending == SLOTS && !deliverOldest(sink, false)) {
            waits++;
            deliverOldest(sink, true);
        }
        while (pending > 0 && deliverOldest(sink, false)) {
            // Hands over every copy the GPU has finished.
        }
        halve(main.width, main.height);
        int readBound = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int drawBound = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int packBound = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        Level source = new Level(main.frameBufferId, 0, main.width, main.height);
        for (Level half : halves) {
            blit(source, half);
            source = half;
        }
        blit(source, target);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.framebuffer());
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, buffers[next]);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
        GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
        fences[next] = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        stamps[next] = nanos;
        next = (next + 1) % SLOTS;
        pending++;
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBound);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readBound);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawBound);
    }

    /** Hands over every copy still under way, waiting for the GPU. */
    void flush(FrameSink sink) {
        while (pending > 0) {
            deliverOldest(sink, true);
        }
    }

    /** How often a copy had to be waited for because the GPU was more than {@value #SLOTS} frames behind. */
    int waits() {
        return waits;
    }

    @Override
    public void close() {
        for (int slot = 0; slot < SLOTS; slot++) {
            if (fences[slot] != 0) {
                GL32.glDeleteSync(fences[slot]);
            }
            GL15.glDeleteBuffers(buffers[slot]);
        }
        halves.forEach(Level::close);
        target.close();
    }

    private boolean deliverOldest(FrameSink sink, boolean wait) {
        int slot = Math.floorMod(next - pending, SLOTS);
        int status = GL32.glClientWaitSync(fences[slot], 0, 0);
        if (status == GL32.GL_TIMEOUT_EXPIRED) {
            if (!wait) {
                return false;
            }
            GL32.glClientWaitSync(fences[slot], GL32.GL_SYNC_FLUSH_COMMANDS_BIT, WAIT_NANOS);
        }
        GL32.glDeleteSync(fences[slot]);
        fences[slot] = 0;
        int packBound = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, buffers[slot]);
        ByteBuffer pixels = GL30.glMapBufferRange(GL21.GL_PIXEL_PACK_BUFFER, 0, (long) width * height * 4,
            GL30.GL_MAP_READ_BIT);
        if (pixels != null) {
            sink.frame(stamps[slot], pixels);
        }
        GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBound);
        pending--;
        return true;
    }

    private void halve(int frameWidth, int frameHeight) {
        if (frameWidth == sourceWidth && frameHeight == sourceHeight) {
            return;
        }
        sourceWidth = frameWidth;
        sourceHeight = frameHeight;
        halves.forEach(Level::close);
        halves.clear();
        int halfWidth = frameWidth;
        int halfHeight = frameHeight;
        while (halfWidth > 2 * width) {
            halfWidth /= 2;
            halfHeight = Math.max(1, halfHeight / 2);
            halves.add(Level.create(halfWidth, halfHeight));
        }
    }

    private static void blit(Level from, Level to) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from.framebuffer());
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to.framebuffer());
        GL30.glBlitFramebuffer(0, 0, from.width(), from.height(), 0, 0, to.width(), to.height(),
            GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
    }
}
