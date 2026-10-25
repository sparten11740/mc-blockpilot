package dev.blockpilot.protocol;

import dev.blockpilot.api.Args;
import dev.blockpilot.api.Duration;

/** A parsed step: the grammar's {@code statement}, with its constructs. */
sealed interface Statement {
    /** A command with its arguments; {@code full} asks a diffable command for the whole output. */
    record Call(CommandRegistry.Entry entry, Args args, boolean full) implements Statement {
    }

    record WaitTicks(Duration duration) implements Statement {
    }

    record WaitIdle() implements Statement {
    }

    /** Re-runs the query until the condition has held {@code stable} times, checking every {@code every} ticks. */
    record WaitUntil(Call query, Condition condition, int stable, int every, Duration timeout) implements Statement {
    }

    /** {@code during}: the action or wait sampled beside, or null; {@code count} caps the samples. */
    record Sample(Call query, Duration every, int count, Statement during) implements Statement {
    }

    record Assert(Call query, Condition condition) implements Statement {
    }

    /** {@code soft} is the {@code ?} written before the statement after {@code then}. */
    record If(Call query, Condition condition, Statement then, boolean soft) implements Statement {
    }
}
