package dev.blockpilot.game;

import java.util.Objects;

/**
 * Whether an agent counts as present, and the activity the HUD shows, smoothed over time. One-shot clients open a
 * connection per script and agents run many short actions, so the raw session count and running action change every
 * few ticks; going by them directly makes the HUD flicker and lets the game pause between two scripts.
 */
final class AgentPresence {
    static final long GRACE_MILLIS = 60_000;
    /** Without a running action for this long, the HUD goes back to "agent active". */
    static final long IDLE_MILLIS = 2_000;
    static final long MIN_SHOWN_MILLIS = 500;
    /** Far enough from the limit that {@code now - NEVER} can't overflow. */
    private static final long NEVER = Long.MIN_VALUE / 2;

    private long lastConnected = NEVER;
    private long lastActive = NEVER;
    private String lastActivity;
    private String shown;
    private long shownSince = NEVER;

    /** {@code activity} is the running action, or null. */
    void update(int sessions, String activity, long now) {
        if (sessions > 0) {
            lastConnected = now;
        }
        if (activity != null) {
            lastActive = now;
            lastActivity = activity;
        }
        String wanted = now - lastActive < IDLE_MILLIS ? lastActivity : null;
        if (!Objects.equals(wanted, shown) && now - shownSince >= MIN_SHOWN_MILLIS) {
            shown = wanted;
            shownSince = now;
        }
    }

    boolean present(long now) {
        return now - lastConnected < GRACE_MILLIS;
    }

    /** Null while idle. */
    String shownActivity() {
        return shown;
    }
}
