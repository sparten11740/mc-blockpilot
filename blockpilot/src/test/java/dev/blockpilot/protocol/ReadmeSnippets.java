package dev.blockpilot.protocol;

import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.BlockpilotApi;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;

/**
 * The README's extension examples ("Extending it"), copied verbatim so they are compiled and run; only Aerolarge's own
 * classes are stand-ins. Keep the two in sync.
 */
final class ReadmeSnippets {
    private ReadmeSnippets() {
    }

    /** Stands in for Aerolarge's landing gear. */
    static final class Gear {
        static boolean isExtended() {
            return true;
        }
    }

    /** Stands in for an Aerolarge blueprint build that takes two ticks. */
    static final class Build {
        private int remaining = 2;

        static Build start(String blueprint) {
            return new Build();
        }

        boolean done() {
            return --remaining <= 0;
        }

        int count() {
            return 42;
        }
    }

    static void register() {
        BlockpilotApi.register("aerolarge", CommandSpec.query("gear", (context, args) ->
                Result.ok(new Fields().put("extended", Gear.isExtended(), "extended=" + Gear.isExtended())))
            .help("landing gear state").field("extended", "true when down"));

        BlockpilotApi.register("aerolarge", CommandSpec.action("build", (context, args) -> {
                Build build = Build.start(args.text("blueprint"));
                // null while still running; the result ends the action.
                return () -> build.done()
                    ? Result.ok(new Fields().put("blocks", build.count(), "blocks=" + build.count()))
                    : null;
            }).onServer().arg("blueprint", ArgType.TEXT).help("builds a blueprint at the player"));
    }

    static void emit(String id) {
        BlockpilotApi.emit("aerolarge", "assembled", new Fields().put("uuid", id,
            "uuid=" + id));
    }
}
