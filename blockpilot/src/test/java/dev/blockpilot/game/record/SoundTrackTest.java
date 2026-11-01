package dev.blockpilot.game.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SoundTrackTest {
    private static final long MILLIS = 1_000_000L;

    @TempDir
    Path directory;

    @Test
    void framesBeforeTheStartAreSkipped() throws Exception {
        SoundTrack track = track();
        // 10 ms before the start: the chunk's first 480 frames come before it.
        track.add(-10 * MILLIS, chunk(960, 0));
        track.end(10 * MILLIS);
        assertTrue(track.complete());
        track.close();
        short[] samples = samples();
        assertEquals(960, samples.length);
        assertEquals(480, samples[0], "the first frame written is the chunk's 480th");
    }

    @Test
    void aGapBecomesSilenceAndAnOverlapIsSkipped() throws Exception {
        SoundTrack track = track();
        track.end(30 * MILLIS);
        track.add(0, chunk(480, 0));
        // 5 ms of nothing, then a chunk that starts 2 ms before the end of the one after the gap.
        track.add(15 * MILLIS, chunk(480, 1000));
        track.add(23 * MILLIS, chunk(480, 5000));
        track.close();
        short[] samples = samples();
        assertEquals(1440 * 2, samples.length);
        assertEquals(479, samples[479 * 2]);
        assertEquals(0, samples[480 * 2], "silence for the gap");
        assertEquals(1000, samples[720 * 2]);
        assertEquals(5000 + 96, samples[1200 * 2], "the overlapping 96 frames are skipped");
    }

    @Test
    void nothingPastTheEndIsWrittenAndAMissingTailIsSilence() throws Exception {
        SoundTrack track = track();
        track.end(5 * MILLIS);
        assertFalse(track.complete());
        track.add(0, chunk(48, 1));
        track.close();
        assertEquals(240 * 2, samples().length);

        SoundTrack cut = new SoundTrack(new WavWriter(directory.resolve("cut.wav")));
        cut.end(MILLIS);
        cut.add(0, chunk(480, 1));
        assertTrue(cut.complete());
        cut.close();
        assertEquals(44 + 48 * WavWriter.BYTES_PER_FRAME, Files.size(directory.resolve("cut.wav")));
    }

    private SoundTrack track() throws Exception {
        return new SoundTrack(new WavWriter(directory.resolve("track.wav")));
    }

    /** Frames whose left sample counts up from {@code first} and whose right one is silent. */
    private static ByteBuffer chunk(int frames, int first) {
        ByteBuffer chunk = ByteBuffer.allocate(frames * WavWriter.BYTES_PER_FRAME).order(ByteOrder.LITTLE_ENDIAN);
        for (int frame = 0; frame < frames; frame++) {
            chunk.putShort((short) (first + frame)).putShort((short) 0);
        }
        return chunk.flip();
    }

    private short[] samples() throws Exception {
        ByteBuffer bytes = ByteBuffer.wrap(Files.readAllBytes(directory.resolve("track.wav")))
            .order(ByteOrder.LITTLE_ENDIAN).position(44);
        short[] samples = new short[bytes.remaining() / 2];
        bytes.asShortBuffer().get(samples);
        return samples;
    }
}
