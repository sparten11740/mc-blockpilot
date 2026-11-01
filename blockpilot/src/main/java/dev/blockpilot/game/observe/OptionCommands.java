package dev.blockpilot.game.observe;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Duration;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import dev.blockpilot.protocol.CommandRegistry;
import java.util.ArrayList;
import java.util.List;

/** {@code option}: lists, reads and sets the client's video and sound options, and puts back what agents changed. */
public final class OptionCommands {
    public void register(CommandRegistry registry) {
        registry.register(CommandSpec.action("option", this::option).group("observe")
            .form(form -> { })
            .form(form -> form.keyword("op", "reset"))
            .form(form -> form.arg("name", ArgType.WORD).optionalArg("value", ArgType.WORD))
            .help("option: the video and sound options and their values; option <name>: its value and what it "
                + "takes; option <name> <value>: sets it and answers once the chunks have arrived and rebuilt, as "
                + "settle does (settled=<s>, or unsettled pending=<n> at timeout=, default 20s); option reset: puts "
                + "back what agents changed, as leave, quitting, the agent-control key and shutdown do, with the "
                + "options the game changed along with them. Names as in options.txt (renderDistance, particles, "
                + "soundCategory_music, …) plus hud on|off. Never written to options.txt")
            .field("was", "the value before the first agent change").field("takes", "the accepted values")
            .field("settled", "seconds until the chunks had arrived and were rebuilt")
            .field("missing", "chunks still to arrive at the timeout, when there are any")
            .example("option renderDistance 8"));
    }

    private Action option(CommandContext context, Args args) throws CommandException {
        if (!args.has("op") && !args.has("name")) {
            List<Row> rows = new ArrayList<>();
            for (ClientOptions.Setting setting : ClientOptions.all()) {
                rows.add(Row.of(setting.name(), describe(setting)));
            }
            return () -> Result.ok().withRows(rows);
        }
        Duration timeout = args.durationOption("timeout");
        if (args.has("op")) {
            List<String> restored = ClientOptions.restoreAll();
            String text = "restored " + (restored.isEmpty() ? "nothing" : String.join(" ", restored));
            return new Settle("option", () -> new Fields().put("restored", restored, text), timeout);
        }
        ClientOptions.Setting setting = ClientOptions.setting(args.word("name"));
        if (!args.has("value")) {
            Fields fields = describe(setting).put("takes", setting.accepts(), "takes " + setting.accepts());
            return () -> Result.ok(fields);
        }
        ClientOptions.set(setting, args.word("value"));
        return new Settle("option", () -> describe(setting), timeout);
    }

    private static Fields describe(ClientOptions.Setting setting) {
        Fields fields = new Fields().bare("option", setting.name()).bare("value", setting.value());
        String original = ClientOptions.original(setting);
        if (original != null && !original.equals(setting.value())) {
            fields.put("was", original, "was " + original);
        }
        return fields;
    }
}
