package dev.blockpilot.protocol;

import java.util.List;

/** Help for the grammar's constructs, which belong to the language rather than to the command registry. */
record Construct(String name, List<String> usages, String help, String example) {
    static final List<Construct> ALL = List.of(
        new Construct("wait", List.of("wait <duration>", "wait idle",
            "wait <query> until <predicate> [and …] [stable=<duration>] [every=<duration>] [timeout=<duration>] "
                + "[clock=server|client]"),
            "waits a time, until the action queue is idle, or until the read-only query's predicates hold; "
                + "predicates are <field><op><value> with == != < <= > >=, or ok, and a value $<field> compares with "
                + "another field (hp<$max_hp); stable= how long they must keep holding (default 1t), every= how "
                + "often to check (default 1t), timeout= (default 60s, a plain number is seconds), clock= the side "
                + "the query runs on; server-side queries are checked at the end of server ticks and report the "
                + "tick (t<n>); a query that fails with err syntax ends the wait at once, other errors are retried",
            "wait block ~1,0,0 until id==air timeout=5s"),
        new Construct("sample", List.of("sample <query> every <duration> x<n> [clock=server|client]",
            "sample <query> every <duration> [x<n>] [clock=server|client] while <action|wait>"),
            "runs the read-only query n times (x1 to x1000), every N ticks (1t or more), one row per run with the "
                + "game tick and the query's fields; with while, runs the action or wait and samples beside it until "
                + "it ends, and the line is its result with the samples as rows",
            "sample status every 1t x20"),
        new Construct("assert", List.of("assert <query> <predicate> [and …]"),
            "runs the read-only query once; ok with the observed fields when the predicates (as in wait) hold, "
                + "else err assert_failed; ?assert goes on",
            "assert block ~0,-1,0 id==grass_block"),
        new Construct("if", List.of("if <query> <predicate> [and …] then <statement>"),
            "runs the statement when the predicates hold (its result is the line), else ok false with the observed "
                + "fields; ?if makes the statement soft too",
            "if status hp<10 then chat low on health"));

    static Construct named(String name) {
        for (Construct construct : ALL) {
            if (construct.name.equals(name)) {
                return construct;
            }
        }
        return null;
    }
}
