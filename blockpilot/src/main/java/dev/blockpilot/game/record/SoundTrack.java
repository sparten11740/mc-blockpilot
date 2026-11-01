package dev.blockpilot.game.record;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * The recording's sound: chunks of the game's mix, each stamped with when its first frame plays on the recording's
 * clock, written to the WAV where that time falls. A gap, such as the sound engine reopening its device, becomes
 * silence; an overlap is skipped; nothing past the end is written. Chunks come from the audio thread, the end from the
 * client thread.
 */
final class SoundTrack {
    private static final long NANOS = 1_000_000_000L;
    private static final int SILENCE_FRAMES = WavWriter.RATE / 10;

    private final WavWriter wav;
    private long written;
    private long endFrame = Long.MAX_VALUE;
    private IOException failure;

    SoundTrack(WavWriter wav) {
        this.wav = wav;
    }

    /** {@code nanos}: when the chunk's first frame plays, from the recording's start; frames as the WAV takes them. */
    synchronized void add(long nanos, ByteBuffer frames) {
        if (failure != null || written >= endFrame) {
            return;
        }
        long position = frameAt(nanos);
        long count = frames.remaining() / WavWriter.BYTES_PER_FRAME;
        try {
            silence(Math.min(position, endFrame));
            long from = Math.max(written, position);
            long to = Math.min(position + count, endFrame);
            if (to > from) {
                int offset = frames.position() + (int) (from - position) * WavWriter.BYTES_PER_FRAME;
                wav.write(frames.slice(offset, (int) (to - from) * WavWriter.BYTES_PER_FRAME));
                written = to;
            }
        } catch (IOException e) {
            failure = e;
        }
    }

    synchronized void end(long nanos) {
        endFrame = frameAt(nanos);
    }

    synchronized boolean complete() {
        return written >= endFrame;
    }

    /** Fills what is missing up to the end with silence and closes the file. */
    synchronized void close() throws IOException {
        try {
            if (failure == null && endFrame != Long.MAX_VALUE) {
                silence(endFrame);
            }
        } finally {
            wav.close();
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void silence(long until) throws IOException {
        while (written < until) {
            int frames = (int) Math.min(until - written, SILENCE_FRAMES);
            wav.write(ByteBuffer.allocate(frames * WavWriter.BYTES_PER_FRAME));
            written += frames;
        }
    }

    private static long frameAt(long nanos) {
        return Math.floorDiv(nanos * WavWriter.RATE, NANOS);
    }
}
