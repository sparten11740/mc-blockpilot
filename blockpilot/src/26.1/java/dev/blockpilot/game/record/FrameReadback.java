package dev.blockpilot.game.record;

import com.mojang.blaze3d.buffers.GpuBuffer;
//? if >=26.2 {
/*import com.mojang.blaze3d.buffers.GpuBufferSlice;
*///?}
import com.mojang.blaze3d.pipeline.RenderTarget;
//? if <26.2 {
import com.mojang.blaze3d.systems.CommandEncoder;
//?}
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
//? if >=26.3 {
/*import com.mojang.renderpearl.api.GpuFormat;
*///?} else if >=26.2 {
/*import com.mojang.blaze3d.GpuFormat;
*///?} else {
import com.mojang.blaze3d.textures.TextureFormat;
//?}
import dev.blockpilot.game.FrameCapture;
import java.util.ArrayList;
import java.util.List;
//? if >=26.2 {
/*import java.util.Optional;
*///?} else {
import java.util.OptionalInt;
//?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Finished frames read back from the GPU at the recording's size, as the game's own Tracy capture does: drawn scaled
 * into a texture of that size, copied into a buffer, and read once the GPU reports the copy done, a frame or two
 * later. A frame more than twice the size is halved in steps first, so the linear filter doesn't skip pixels. Client
 * thread only.
 */
final class FrameReadback implements AutoCloseable {
    private static final int SLOTS = 3;
    private static final int RENDERED = GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING;

    private record Level(GpuTexture texture, GpuTextureView view) {
        void close() {
            view.close();
            texture.close();
        }
    }

    private final int width;
    private final int height;
    private final Level target;
    private final GpuBuffer[] buffers = new GpuBuffer[SLOTS];
    private final long[] stamps = new long[SLOTS];
    /** Bumped per copy, so a callback of a copy that was read early can't mark the slot's next copy done. */
    private final int[] generations = new int[SLOTS];
    private final boolean[] copied = new boolean[SLOTS];
    private final List<Level> halves = new ArrayList<>();
    private int sourceWidth;
    private int sourceHeight;
    private int next;
    private int pending;
    private int waits;

    FrameReadback(int width, int height) {
        this.width = width;
        this.height = height;
        GpuDevice device = RenderSystem.getDevice();
        target = level(device, width, height, GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC);
        for (int slot = 0; slot < SLOTS; slot++) {
            buffers[slot] = device.createBuffer(() -> "Blockpilot recording",
                GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, (long) width * height * 4);
        }
    }

    /** Copies the frame just finished, stamped {@code nanos}, and hands the copies already done to the sink. */
    void capture(long nanos, FrameSink sink) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        GpuTextureView source = main.getColorTextureView();
        if (source == null) {
            return;
        }
        if (pending == SLOTS && !deliverOldest(sink, false)) {
            waits++;
            FrameCapture.awaitCopies();
            deliverOldest(sink, true);
        }
        while (pending > 0 && deliverOldest(sink, false)) {
            // Hands over every copy the GPU has finished.
        }
        halve(main.width, main.height);
        GpuDevice device = RenderSystem.getDevice();
        for (Level half : halves) {
            blit(device, source, half.view());
            source = half.view();
        }
        blit(device, source, target.view());
        int slot = next;
        int generation = ++generations[slot];
        copied[slot] = false;
        stamps[slot] = nanos;
        device.createCommandEncoder().copyTextureToBuffer(target.texture(), buffers[slot], 0L, () -> {
            if (generations[slot] == generation) {
                copied[slot] = true;
            }
        }, 0);
        next = (next + 1) % SLOTS;
        pending++;
    }

    /** Hands over every copy still under way, waiting for the GPU. */
    void flush(FrameSink sink) {
        if (pending > 0) {
            FrameCapture.awaitCopies();
        }
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
        halves.forEach(Level::close);
        target.close();
        for (GpuBuffer buffer : buffers) {
            buffer.close();
        }
    }

    /** Hands over the oldest copy if its callback has come, or the caller has waited for the GPU. */
    private boolean deliverOldest(FrameSink sink, boolean waited) {
        int slot = Math.floorMod(next - pending, SLOTS);
        if (!copied[slot] && !waited) {
            return false;
        }
        //? if >=26.2 {
        /*try (GpuBufferSlice.MappedView view = buffers[slot].map(true, false)) {
        *///?} else {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView view = encoder.mapBuffer(buffers[slot], true, false)) {
        //?}
            sink.frame(stamps[slot], view.data());
        }
        generations[slot]++;
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
        GpuDevice device = RenderSystem.getDevice();
        int halfWidth = frameWidth;
        int halfHeight = frameHeight;
        while (halfWidth > 2 * width) {
            halfWidth /= 2;
            halfHeight = Math.max(1, halfHeight / 2);
            halves.add(level(device, halfWidth, halfHeight, RENDERED));
        }
    }

    private static Level level(GpuDevice device, int width, int height, int usage) {
        GpuTexture texture = device.createTexture("Blockpilot recording", usage, TextureFormat.RGBA8, width, height,
            1, 1);
        return new Level(texture, device.createTextureView(texture));
    }

    private static void blit(GpuDevice device, GpuTextureView from, GpuTextureView to) {
        //? if >=26.2 {
        /*try (RenderPass pass = device.createCommandEncoder().createRenderPass(() -> "Blockpilot recording", to,
            Optional.empty())) {
        *///?} else {
        try (RenderPass pass = device.createCommandEncoder().createRenderPass(() -> "Blockpilot recording", to,
            OptionalInt.empty())) {
        //?}
            //? if >=26.2 {
            /*RenderSystem.bindDefaultUniforms(pass);
            *///?}
            pass.setPipeline(RenderPipelines.TRACY_BLIT);
            pass.bindTexture("InSampler", from, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(0, 3);
        }
    }
}
