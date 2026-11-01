package dev.blockpilot.game.record;

import java.nio.ByteBuffer;

/** Takes the frames a {@code FrameReadback} hands over, in order: RGBA, rows bottom up, valid only during the call. */
interface FrameSink {
    void frame(long nanos, ByteBuffer rgba);
}
