package dev.blockpilot.game.record;

import dev.blockpilot.Blockpilot;
import dev.blockpilot.game.observe.HudVisibility;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.client.Minecraft;

/**
 * One recording: frames read back on the client thread, written to ffmpeg's input by a thread of its own, encoded into
 * a temporary Matroska video, and muxed into the MP4 at the end with the sound, written to a temporary WAV meanwhile.
 * When the writer falls behind, the client thread waits for a free buffer at the next frame instead of dropping frames,
 * so a slow encoder shows as repeated frames, as a slow game does.
 */
final class Recording {
    record Summary(Path file, double seconds, int width, int height, int fps, long frames, long repeats,
        boolean audio, String encoder) {
    }

    private record Frame(long nanos, ByteBuffer pixels, boolean end) {
    }

    /** Frames waiting for the writer; with the one it holds and the one being filled, the buffers there are. */
    private static final int QUEUE = 4;
    private static final int ERROR_LINES = 5;
    private static final long ERRORS_WAIT_MILLIS = 5_000;
    /** How long the client thread waits for the writer to free a buffer before giving the encoder up. */
    private static final long STALL_SECONDS = 10;
    /** How long stopping waits for the sound to reach the end; the audio thread renders about every 10 ms. */
    private static final long SOUND_WAIT_NANOS = 200_000_000L;
    private static final long NANOS = 1_000_000_000L;

    private final String name;
    private final Path output;
    private final Path video;
    private final Path sound;
    private final int width;
    private final int height;
    private final int fps;
    private final Ffmpeg.Tool ffmpeg;
    private final boolean hideHud;
    private final boolean hudBefore;
    private final Process encoder;
    private final FrameReadback readback;
    private final FrameSlots slots;
    /** Null for a silent recording. */
    private final SoundTrack track;
    private final BlockingQueue<Frame> queue = new LinkedBlockingQueue<>();
    private final BlockingQueue<ByteBuffer> free = new ArrayBlockingQueue<>(QUEUE + 2);
    private final Deque<String> errorLines = new ArrayDeque<>();
    private final Thread errors;
    private final Thread writer;
    private final CompletableFuture<Summary> finished = new CompletableFuture<>();
    private volatile long startNanos = -1;
    private int readBack;
    private boolean stopping;
    private volatile String failure;

    Recording(String name, Path directory, int width, int height, int fps, Ffmpeg.Tool ffmpeg, boolean hideHud,
        boolean audio) throws IOException {
        this.name = name;
        this.output = directory.resolve(name + ".mp4");
        this.video = directory.resolve("." + name + ".video.mkv");
        this.sound = directory.resolve("." + name + ".wav");
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.ffmpeg = ffmpeg;
        this.hideHud = hideHud;
        hudBefore = HudVisibility.hidden();
        slots = new FrameSlots(fps);
        encoder = new ProcessBuilder(FfmpegCommands.encode(ffmpeg.path(), ffmpeg.encoder(), width, height, fps, video))
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        errors = new Thread(this::collectErrors, "Blockpilot recording ffmpeg output");
        errors.setDaemon(true);
        errors.start();
        try {
            for (int buffer = 0; buffer < QUEUE + 2; buffer++) {
                free.add(ByteBuffer.allocate(width * height * 4));
            }
            readback = new FrameReadback(width, height);
            track = audio ? new SoundTrack(new WavWriter(sound)) : null;
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            encoder.destroyForcibly();
            throw e;
        }
        writer = new Thread(this::write, "Blockpilot recording writer");
        writer.setDaemon(true);
        writer.start();
        if (track != null) {
            AudioCapture.listen(this::sound);
        }
        hideHud();
    }

    String name() {
        return name;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    int fps() {
        return fps;
    }

    boolean audio() {
        return track != null;
    }

    boolean stopping() {
        return stopping;
    }

    boolean hasFrames() {
        return startNanos >= 0;
    }

    int framesReadBack() {
        return readBack;
    }

    double seconds() {
        return startNanos < 0 ? 0 : (System.nanoTime() - startNanos) / (double) NANOS;
    }

    /** Null while the encoder runs; ffmpeg's last lines once it failed. */
    String failure() {
        return failure;
    }

    CompletableFuture<Summary> finished() {
        return finished;
    }

    /** A frame has just been finished; client thread. */
    void frame() {
        if (stopping || failure != null) {
            return;
        }
        long now = System.nanoTime();
        if (startNanos < 0) {
            startNanos = now;
        }
        hideHud();
        readback.capture(now - startNanos, this::enqueue);
    }

    /** Ends the recording at this moment and finishes the file in the background; client thread. */
    void stop() {
        if (stopping) {
            return;
        }
        stopping = true;
        long endNanos = startNanos < 0 ? 0 : System.nanoTime() - startNanos;
        restoreHud();
        readback.flush(this::enqueue);
        readback.close();
        Blockpilot.LOGGER.info("Recording {}: {} frames read back at {}x{}, {} of them waited for", name, readBack,
            width, height, readback.waits());
        queue.add(new Frame(endNanos, null, true));
        if (track != null) {
            track.end(endNanos);
            long deadline = System.nanoTime() + SOUND_WAIT_NANOS;
            while (!track.complete() && System.nanoTime() < deadline) {
                LockSupport.parkNanos(SOUND_WAIT_NANOS / 100);
            }
            stopSound();
        }
        Thread finisher = new Thread(this::finish, "Blockpilot recording finisher");
        finisher.setDaemon(true);
        finisher.start();
    }

    /** After a failure: drops what is under way and deletes the files; client thread. */
    void abandon() {
        stopping = true;
        restoreHud();
        readback.close();
        queue.add(new Frame(0, null, true));
        encoder.destroyForcibly();
        if (track != null) {
            stopSound();
            try {
                track.close();
            } catch (IOException e) {
                Blockpilot.LOGGER.warn("Could not close {}", sound, e);
            }
        }
        deleteQuietly(video);
        deleteQuietly(sound);
        deleteQuietly(output);
        finished.completeExceptionally(new IOException(failure));
    }

    /** Puts the sound engine back on its own device; client thread. */
    private void stopSound() {
        AudioCapture.listen(null);
        AudioCapture.use(false);
    }

    private void sound(long nanos, ByteBuffer frames) {
        long start = startNanos;
        if (start >= 0) {
            track.add(nanos - start, frames);
        }
    }

    private void enqueue(long nanos, ByteBuffer rgba) {
        if (failure != null) {
            return;
        }
        try {
            ByteBuffer pixels = free.poll(STALL_SECONDS, TimeUnit.SECONDS);
            if (pixels == null) {
                failure = "ffmpeg took no frame for " + STALL_SECONDS + " s";
                return;
            }
            pixels.clear().put(rgba).flip();
            queue.add(new Frame(nanos, pixels, false));
            readBack++;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void write() {
        ByteBuffer held = null;
        try (OutputStream frames = encoder.getOutputStream()) {
            while (true) {
                Frame frame = queue.take();
                if (failure != null) {
                    if (frame.end()) {
                        return;
                    }
                    free.add(frame.pixels());
                    continue;
                }
                int copies = frame.end() ? slots.end(frame.nanos()) : slots.replace(frame.nanos());
                try {
                    for (int copy = 0; copy < copies; copy++) {
                        frames.write(held.array(), 0, held.limit());
                    }
                } catch (IOException e) {
                    failure = errors("ffmpeg stopped taking frames: " + e.getMessage());
                }
                if (held != null) {
                    free.add(held);
                }
                if (frame.end()) {
                    return;
                }
                held = frame.pixels();
            }
        } catch (IOException e) {
            if (failure == null) {
                failure = errors("ffmpeg failed: " + e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void finish() {
        try {
            writer.join();
            int exit = encoder.waitFor();
            if (failure != null || exit != 0) {
                throw new IOException(failure != null ? failure : errors("ffmpeg exited with " + exit));
            }
            if (track != null) {
                track.close();
            }
            Process mux = new ProcessBuilder(FfmpegCommands.mux(ffmpeg.path(), video, track != null ? sound : null,
                output))
                .redirectErrorStream(true).start();
            String muxOutput = new String(mux.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            if (mux.waitFor() != 0) {
                throw new IOException("muxing failed: " + muxOutput);
            }
            deleteQuietly(video);
            deleteQuietly(sound);
            finished.complete(new Summary(output, slots.nanos() / (double) NANOS, width, height, fps, slots.slots(),
                slots.repeats(), track != null, ffmpeg.encoder()));
        } catch (IOException e) {
            failure = e.getMessage();
            deleteQuietly(video);
            deleteQuietly(sound);
            deleteQuietly(output);
            finished.completeExceptionally(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finished.completeExceptionally(e);
        }
    }

    private void collectErrors() {
        try {
            for (String line : new String(encoder.getErrorStream().readAllBytes(), StandardCharsets.UTF_8)
                .lines().toList()) {
                addErrorLine(line);
            }
        } catch (IOException e) {
            addErrorLine(e.getMessage());
        }
    }

    private void addErrorLine(String line) {
        synchronized (errorLines) {
            errorLines.addLast(line);
            if (errorLines.size() > ERROR_LINES) {
                errorLines.removeFirst();
            }
        }
    }

    /** The reason with ffmpeg's last lines, once it has exited and they are all in. */
    private String errors(String reason) {
        try {
            errors.join(ERRORS_WAIT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        List<String> lines;
        synchronized (errorLines) {
            lines = List.copyOf(errorLines);
        }
        return lines.isEmpty() ? reason : reason + ": " + String.join(" | ", lines);
    }

    private void hideHud() {
        if (hideHud) {
            HudVisibility.setHidden(true);
        }
    }

    private void restoreHud() {
        if (hideHud) {
            HudVisibility.setHidden(hudBefore);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            Blockpilot.LOGGER.warn("Could not delete {}", file, e);
        }
    }
}
