package dev.blockpilot.game.observe;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.Result;
import java.util.function.Supplier;

/** Small actions shared by the observation commands. */
final class ActionSupport {
    private ActionSupport() {
    }

    /** Waits the given number of ticks (so the next frames render), then answers. */
    static Action afterFrames(int ticks, Supplier<Result> answer) {
        int[] left = {ticks};
        return () -> --left[0] <= 0 ? answer.get() : null;
    }
}
