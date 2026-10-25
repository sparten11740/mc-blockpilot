package dev.blockpilot.protocol;

import java.util.List;

/** Help for the grammar's constructs, which belong to the language rather than to the command registry. */
record Construct(String name, List<String> usages, String help, String example) {
    static final List<Construct> ALL = List.of(
        new Construct("wait", List.of("wait <duration>", "wait idle",
            "wait <query> until <predicate> [and …] [stable=…] [every=…] [timeout=…] [clock=server]"),
            "waits a time, until the action queue is idle, or until the read-only query's predicates hold; "
                + "predicates are <field><op><value> with == != < <= > >=, or ok, and a value $<field> compares with "
                + "another field (hp<$max_hp); stable= consecutive matches (default 1t), every= check interval "
                + "(default 1t), timeout= (default 60s, a plain number is seconds); server-side queries are checked "
                + "at the end of server ticks and report the tick (t<n>)",
            "wait block ~1,0,0 until id==air timeout=5s"),
        new Construct("sample", List.of("sample <query> every <duration> <count> [clock=server]"),
            "runs the read-only query n times (max 1000), every N ticks, one row per run with the game tick and "
                + "the query's fields",
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
