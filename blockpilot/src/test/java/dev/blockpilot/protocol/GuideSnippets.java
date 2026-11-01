package dev.blockpilot.protocol;

import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.BlockpilotApi;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Duration;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import java.util.List;

/**
 * The examples in docs/extending.md, copied verbatim so they are compiled and run; only the mod's own classes are
 * stand-ins. Keep the two in sync.
 */
final class GuideSnippets {
    private GuideSnippets() {
    }

    /** Stands in for a mod's landing gear. */
    static final class Gear {
        static boolean isExtended() {
            return true;
        }
    }

    /** Stands in for a mod's flaps, which settle at once. */
    static final class Flaps {
        static void move(String side, double degrees, int ticks) {
        }

        static boolean settled() {
            return true;
        }
    }

    /** Stands in for a mod's blueprint build that takes two ticks. */
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
        BlockpilotApi.register("mymod", CommandSpec.query("gear", (context, args) ->
                Result.ok(new Fields().put("extended", Gear.isExtended(), "extended=" + Gear.isExtended())))
            .help("landing gear state").field("extended", "true when down"));

        BlockpilotApi.register("mymod", CommandSpec.action("build", (context, args) -> {
                Build build = Build.start(args.text("blueprint"));
                // null while still running; the result ends the action.
                return () -> build.done()
                    ? Result.ok(new Fields().put("blocks", build.count(), "blocks=" + build.count()))
                    : null;
            }).onServer().arg("blueprint", ArgType.TEXT).help("builds a blueprint at the player"));

        BlockpilotApi.register("mymod", CommandSpec.action("flaps", (context, args) -> {
                double degrees = args.word("op").equals("retract") ? 0 : args.number("degrees", 0);
                Duration over = args.has("ticks") ? args.duration("ticks") : new Duration(1);
                Flaps.move(args.option("side"), degrees, over.ticks());
                return () -> Flaps.settled() ? Result.ok() : null;
            })
            .form(form -> form.keyword("op", "set").arg("degrees", ArgType.NUMBER)
                .optionalArg("ticks", ArgType.DURATION))
            .form(form -> form.keyword("op", "retract"))
            .option("side", "one wing only", List.of("left", "right"))
            .help("moves the flaps").example("mymod:flaps set 15 20t"));
    }

    static void emit(String id) {
        BlockpilotApi.emit("mymod", "assembled", new Fields().put("uuid", id,
            "uuid=" + id));
    }
}
