package dev.blockpilot.game.load;

/**
 * When the packets of one direction go on: each after its delay, but never before the one ahead of it, so jitter
 * only bunches them, as on a real connection. Used on one thread, the channel's event loop.
 */
final class Release {
    private long last;
    private int held;

    /** Nanoseconds to hold a packet arriving now, or -1 to pass it on at once: no delay and none held. */
    long hold(long now, long delay) {
        if (delay == 0 && held == 0) {
            return -1;
        }
        held++;
        last = Math.max(now + delay, last);
        return last - now;
    }

    /** A held packet went on. */
    void released() {
        held--;
    }

    boolean holding() {
        return held > 0;
    }
}
