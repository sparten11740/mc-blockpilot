package dev.blockpilot.game.observe;

import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.Duration;

/** {@code since=} on the observation logs: a tick number, or a duration that long before the current tick. */
final class Since {
    private Since() {
    }

    static long tick(Args args, long now, long fallback) throws CommandException {
        String value = args.option("since");
        if (value == null) {
            return fallback;
        }
        if (args.optionType("since") == ArgType.DURATION) {
            return now - Duration.parse(value).ticks();
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new CommandException("syntax", "since= expects a tick number or a duration like 60t");
        }
    }
}
