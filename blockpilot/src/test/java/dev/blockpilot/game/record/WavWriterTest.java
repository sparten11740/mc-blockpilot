package dev.blockpilot.game.record;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WavWriterTest {
    @TempDir
    Path directory;

    @Test
    void theHeaderAndSamplesReadBackAsWritten() throws Exception {
        Path file = directory.resolve("take.wav");
        ByteBuffer samples = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        samples.putShort((short) 1).putShort((short) -1).putShort((short) 1000).putShort((short) -1000)
            .putShort(Short.MAX_VALUE).putShort(Short.MIN_VALUE).flip();
        byte[] expected = Arrays.copyOf(samples.array(), 12);
        try (WavWriter wav = new WavWriter(file)) {
            wav.write(samples.slice(0, 4));
            wav.write(samples.slice(4, 8));
            assertEquals(3, wav.frames());
        }
        byte[] bytes = Files.readAllBytes(file);
        ByteBuffer read = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(56, bytes.length);
        assertEquals(48, read.getInt(4));
        assertEquals(12, read.getInt(40));
        assertArrayEquals(expected, Arrays.copyOfRange(bytes, 44, 56));
        AudioFileFormat format = AudioSystem.getAudioFileFormat(file.toFile());
        assertEquals(AudioFileFormat.Type.WAVE, format.getType());
        assertEquals(3, format.getFrameLength());
        assertEquals(new AudioFormat(48_000, 16, 2, true, false).toString(), format.getFormat().toString());
    }

    @Test
    void anEmptyFileIsStillValid() throws Exception {
        Path file = directory.resolve("empty.wav");
        new WavWriter(file).close();
        assertEquals(44, Files.size(file));
        assertEquals(0, AudioSystem.getAudioFileFormat(file.toFile()).getFrameLength());
    }
}
