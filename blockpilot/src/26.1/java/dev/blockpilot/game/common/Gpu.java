package dev.blockpilot.game.common;

import com.mojang.blaze3d.buffers.GpuBuffer;
//? if >=26.2 {
/*import com.mojang.blaze3d.buffers.GpuBufferSlice;
*///?}
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
//? if >=26.2 {
/*import com.mojang.blaze3d.GpuFormat;
*///?} else {
import com.mojang.blaze3d.textures.TextureFormat;
//?}
import java.nio.ByteBuffer;
//? if >=26.2 {
/*import java.util.Optional;
*///?} else {
import java.util.OptionalInt;
//?}
import java.util.function.Consumer;
import net.minecraft.client.renderer.RenderPipelines;
//? if <26.2 {
import org.lwjgl.opengl.GL11C;
//?}

/** What differs in the game's GPU API between 26.x releases. Client thread only. */
public final class Gpu {
    private static final long COPY_WAIT_NANOS = 5_000_000_000L;

    private Gpu() {
    }

    /** Hands a buffer's contents to the reader, which mustn't keep them, as they're only there during the call. */
    public static void read(GpuBuffer buffer, Consumer<ByteBuffer> reader) {
        //? if >=26.2 {
        /*try (GpuBufferSlice.MappedView view = buffer.map(true, false)) {
        *///?} else {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView view = encoder.mapBuffer(buffer, true, false)) {
        //?}
            reader.accept(view.data());
        }
    }

    /** A texture in the format the game renders to. */
    public static GpuTexture texture(String label, int usage, int width, int height) {
        //? if >=26.2 {
        /*return RenderSystem.getDevice().createTexture(label, usage, GpuFormat.RGBA8_UNORM, width, height, 1, 1);
        *///?} else {
        return RenderSystem.getDevice().createTexture(label, usage, TextureFormat.RGBA8, width, height, 1, 1);
        //?}
    }

    public static int pixelSize(GpuTexture texture) {
        //? if >=26.2 {
        /*return texture.getFormat().blockSize();
        *///?} else {
        return texture.getFormat().pixelSize();
        //?}
    }

    /** Draws one texture scaled to fill another, with the shader the game's Tracy capture uses. */
    public static void blit(GpuTextureView from, GpuTextureView to) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        //? if >=26.2 {
        /*try (RenderPass pass = encoder.createRenderPass(() -> "Blockpilot recording", to, Optional.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
        *///?} else {
        try (RenderPass pass = encoder.createRenderPass(() -> "Blockpilot recording", to, OptionalInt.empty())) {
        //?}
            //? if >=26.3 {
            /*pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.TRACY_BLIT));
            pass.setUniform("InSampler", from, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            *///?} else {
            pass.setPipeline(RenderPipelines.TRACY_BLIT);
            pass.bindTexture("InSampler", from, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            //?}
            //? if >=26.2 {
            /*pass.draw(3, 1, 0, 0);
            *///?} else {
            pass.draw(0, 3);
            //?}
        }
    }

    /**
     * Waits until the GPU has done the copies asked for so far. Mapping a buffer doesn't wait: an OpenGL buffer mapped
     * for good (with GL_ARB_buffer_storage) and a Vulkan one only hand over their memory, whatever the copy's state.
     * Only OpenGL without that extension synchronises. Between frames only, as it may end the GPU's work so far.
     */
    public static void awaitCopies() {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuFence fence = encoder.createFence()) {
            //? if >=26.2 {
            /*// A fence stands for the whole submit, which can't be waited for until it has ended.
            encoder.submit();
            *///?} else {
            // Only NeoForge's fence flushes, and without it the commands may never reach the GPU. 26.1 is OpenGL only.
            GL11C.glFlush();
            //?}
            await(fence);
        }
    }

    /** Waits for a fence on a copy, which has to have reached the GPU, as one from an earlier frame has. */
    public static void await(GpuFence fence) {
        if (!fence.awaitCompletion(COPY_WAIT_NANOS)) {
            throw new IllegalStateException("the GPU didn't finish copying a frame within 5 s");
        }
    }
}
