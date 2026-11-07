package dev.blockpilot.protocol;

import dev.blockpilot.api.Result;

/**
 * A refusal raised outside a command's own code while one of its steps runs, as when a slash command the agent typed
 * is held back on its way to the server: that step fails with it.
 */
public final class Refusal {
    private static volatile Result pending;

    private Refusal() {
    }

    public static void raise(String code, String argument) {
        pending = Result.error(code, argument);
    }

    static Result take() {
        Result refused = pending;
        pending = null;
        return refused;
    }
}
