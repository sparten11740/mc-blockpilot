package dev.blockpilot.game.record;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** A 16-bit stereo 48 kHz PCM WAV file, written as the samples come; the header's sizes are filled in on close. */
final class WavWriter implements Closeable {
    static final int RATE = 48_000;
    static final int CHANNELS = 2;
    static final int BYTES_PER_FRAME = CHANNELS * Short.BYTES;
    private static final int HEADER_BYTES = 44;
    /** RIFF sizes are unsigned 32-bit: about six hours of sound. */
    private static final long MAX_SIZE = 0xFFFF_FFFFL;

    private final FileChannel channel;
    private long dataBytes;

    WavWriter(Path file) throws IOException {
        channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE);
        channel.write(header(0));
    }

    /** Interleaved little-endian samples, left first. */
    void write(ByteBuffer samples) throws IOException {
        dataBytes += samples.remaining();
        while (samples.hasRemaining()) {
            channel.write(samples);
        }
    }

    long frames() {
        return dataBytes / BYTES_PER_FRAME;
    }

    @Override
    public void close() throws IOException {
        try {
            channel.write(header(dataBytes), 0);
        } finally {
            channel.close();
        }
    }

    private static ByteBuffer header(long dataBytes) {
        long data = Math.min(dataBytes, MAX_SIZE - (HEADER_BYTES - 8));
        ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        header.put(ascii("RIFF")).putInt((int) (data + HEADER_BYTES - 8)).put(ascii("WAVE"));
        header.put(ascii("fmt ")).putInt(16).putShort((short) 1).putShort((short) CHANNELS).putInt(RATE)
            .putInt(RATE * BYTES_PER_FRAME).putShort((short) BYTES_PER_FRAME).putShort((short) Short.SIZE);
        header.put(ascii("data")).putInt((int) data);
        return header.flip();
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }
}
