package dev.blockpilot.game.observe;

import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.Duration;

/** {@code since=} on the observation logs: a tick number, or a duration that long before the current tick. */
final class Since {
    private Since() {
    }

    /**
     * The first tick a reply covers: where the log still begins when it dropped entries from the asked tick on, else
     * the asked tick, or, when none was asked, the oldest entry, or now for an empty log.
     */
    static long first(long asked, Long from, Long oldest, long now) {
        if (from != null) {
            return from;
        }
        if (asked != Long.MIN_VALUE) {
            return asked;
        }
        return oldest != null ? oldest : now;
    }

    /** Whether {@code since=} is a bare tick from before the player joined this world, likely meant as ticks back. */
    static boolean predatesJoin(Args args, Long joined) {
        return joined != null && args.optionType("since") == ArgType.INT && Long.parseLong(args.option("since"))
            < joined;
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
