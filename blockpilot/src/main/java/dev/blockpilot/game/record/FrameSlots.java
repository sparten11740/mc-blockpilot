package dev.blockpilot.game.record;

/**
 * Which rendered frame fills each frame of a video at a fixed rate: slot {@code k} shows the newest frame finished by
 * {@code k / fps} seconds after the start, repeated while no newer one was. Times are nanoseconds from the start.
 * Frames arrive in order, so a frame's slots are known once the next frame, or the end, comes.
 */
final class FrameSlots {
    private static final long NANOS = 1_000_000_000L;

    private final int fps;
    private long slots;
    private long repeats;
    private boolean holding;

    FrameSlots(int fps) {
        this.fps = fps;
    }

    /** A frame finished at {@code nanos} replaces the one held: how many slots the held one fills. */
    int replace(long nanos) {
        int filled = holding ? fill(slotsBefore(nanos)) : 0;
        holding = true;
        return filled;
    }

    /** The recording ends at {@code nanos}: how many slots the held frame fills. A held frame fills at least one. */
    int end(long nanos) {
        if (!holding) {
            return 0;
        }
        holding = false;
        return fill(Math.max(slotsBefore(nanos), 1));
    }

    long slots() {
        return slots;
    }

    long repeats() {
        return repeats;
    }

    long nanos() {
        return slots * NANOS / fps;
    }

    private int fill(long through) {
        int filled = (int) Math.max(0, through - slots);
        slots += filled;
        repeats += Math.max(0, filled - 1);
        return filled;
    }

    /** The slots that start before that time. */
    private long slotsBefore(long nanos) {
        return Math.ceilDiv(Math.max(0, nanos) * fps, NANOS);
    }
}
