package dev.blockpilot.game.observe;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Duration;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Numbers;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import dev.blockpilot.protocol.CommandRegistry;
import dev.blockpilot.protocol.Engine;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;

/** {@code option}: lists, reads and sets the client's video options, and puts back what agents changed. */
public final class OptionCommands {
    private static final Duration SETTLE_TIMEOUT = Duration.seconds(20);

    public void register(CommandRegistry registry) {
        registry.register(CommandSpec.action("option", this::option).group("observe")
            .optionalArg("name", ArgType.WORD).optionalArg("value", ArgType.WORD)
            .help("option: the video options and their values; option <name>: its value and what it takes; option "
                + "<name> <value>: sets it and answers once the chunks have rebuilt (settled=<s>, or unsettled "
                + "pending=<n> at timeout=, default 20s); option reset: puts back what agents changed, as leave, "
                + "quitting, the agent-control key and shutdown do. Names as in options.txt (graphicsMode, "
                + "renderDistance, particles, …) plus hud on|off. Never written to options.txt")
            .field("was", "the value before the first agent change").field("takes", "the accepted values")
            .field("settled", "seconds until the chunks were rebuilt").example("option graphicsMode fast"));
    }

    private Action option(CommandContext context, Args args) throws CommandException {
        String name = args.word("name");
        if (name == null) {
            List<Row> rows = new ArrayList<>();
            for (ClientOptions.Setting setting : ClientOptions.all()) {
                rows.add(Row.of(setting.name(), describe(setting)));
            }
            return () -> Result.ok().withRows(rows);
        }
        Duration timeout = args.durationOption("timeout");
        int timeoutTicks = (timeout == null ? SETTLE_TIMEOUT : timeout).ticks();
        if (name.equals("reset")) {
            List<String> restored = ClientOptions.restoreAll();
            String text = "restored " + (restored.isEmpty() ? "nothing" : String.join(" ", restored));
            return new Settle(() -> new Fields().put("restored", restored, text), timeoutTicks);
        }
        ClientOptions.Setting setting = ClientOptions.setting(name);
        if (!args.has("value")) {
            Fields fields = describe(setting).put("takes", setting.accepts(), "takes " + setting.accepts());
            return () -> Result.ok(fields);
        }
        ClientOptions.set(setting, args.word("value"));
        return new Settle(() -> describe(setting), timeoutTicks);
    }

    private static Fields describe(ClientOptions.Setting setting) {
        Fields fields = new Fields().bare("option", setting.name()).bare("value", setting.value());
        String original = ClientOptions.original(setting);
        if (original != null && !original.equals(setting.value())) {
            fields.put("was", original, "was " + original);
        }
        return fields;
    }

    /**
     * Waits until the section compile queue has stayed empty, with the same number of sections rendered, for 3 ticks
     * (from the second tick on, once the renderer has scheduled what the change made dirty).
     */
    private static final class Settle implements Action, Engine.SelfTimed {
        private static final int QUIET_TICKS = 3;
        private final Supplier<Fields> answer;
        private final int timeoutTicks;
        private int ticks;
        private int quiet;
        private int lastRendered = -1;

        Settle(Supplier<Fields> answer, int timeoutTicks) {
            this.answer = answer;
            this.timeoutTicks = timeoutTicks;
        }

        @Override
        public Result tick() {
            ticks++;
            var renderer = Minecraft.getInstance().levelRenderer;
            SectionRenderDispatcher dispatcher = renderer.getSectionRenderDispatcher();
            if (Minecraft.getInstance().level == null || dispatcher == null) {
                return Result.ok(answer.get());
            }
            int rendered = renderer.countRenderedSections();
            boolean idle = ticks >= 2 && dispatcher.isQueueEmpty() && rendered == lastRendered;
            lastRendered = rendered;
            quiet = idle ? quiet + 1 : 0;
            double seconds = new Duration(ticks).seconds();
            if (quiet >= QUIET_TICKS) {
                return Result.ok(answer.get().put("settled", seconds, "settled=" + Numbers.format(seconds) + "s"));
            }
            if (ticks >= timeoutTicks) {
                int pending = dispatcher.getToBatchCount() + dispatcher.getToUpload();
                return Result.ok(answer.get().put("unsettled", true, "unsettled")
                    .put("pending", pending, "pending=" + pending));
            }
            return null;
        }

        @Override
        public String label() {
            return "option";
        }
    }
}
