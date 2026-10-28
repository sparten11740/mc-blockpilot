package dev.blockpilot.game.load;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import dev.blockpilot.protocol.CommandRegistry;
import net.minecraft.client.Minecraft;

/** {@code load}: idle time added on purpose to the integrated server's ticks or the client's frames. */
public final class LoadCommands {
    public void register(CommandRegistry registry) {
        registry.register(CommandSpec.action("load", LoadCommands::load).group("performance")
            .form(form -> form.optionalKeyword("op", "off"))
            .form(form -> form.keyword("target", "server", "client").arg("ms", ArgType.INT))
            .option("jitter", "milliseconds each tick or frame may stray from the load either way, up to the load",
                ArgType.INT)
            .help("load server|client <ms> [jitter=<ms>] adds idle time to every integrated-server tick, counted in "
                + "its MSPT, or to every frame; 0 to " + Load.MAX_MS + " ms, 0 ends it; load off ends all, load "
                + "reports what is set; it also ends on leaving the world, with agent control off and on closing, "
                + "and nothing is saved")
            .field("server_ms", "idle time added to each server tick").field("server_jitter", "how far it strays")
            .field("client_ms", "idle time added to each frame").field("client_jitter", "how far it strays")
            .example("load server 60 jitter=20"));
    }

    private static Action load(CommandContext context, Args args) throws CommandException {
        if (args.has("target")) {
            String target = args.word("target");
            int ms = args.integer("ms", 0);
            int jitter = args.intOption("jitter", 0);
            if (ms < 0 || ms > Load.MAX_MS) {
                throw new CommandException("syntax", "load " + target + " takes 0 to " + Load.MAX_MS + " ms, not "
                    + ms);
            }
            if (jitter < 0 || jitter > ms) {
                throw new CommandException("syntax", "jitter= takes 0 to the load, " + ms + " ms, not " + jitter);
            }
            if (target.equals("server") && Minecraft.getInstance().getSingleplayerServer() == null) {
                throw new CommandException("no_server");
            }
            Load.set(target, new Load.Setting(ms, jitter));
        } else if (args.hasOption("jitter")) {
            throw new CommandException("syntax", "jitter= goes with a load: load server|client <ms> jitter=<ms>");
        } else if (args.has("op")) {
            Load.off();
        }
        Fields report = report();
        return () -> Result.ok(report);
    }

    /** What is set, as {@code server=40±10ms client=5ms}, leaving out what is off. */
    static Fields report() {
        Fields fields = new Fields();
        add(fields, "server", Load.server());
        add(fields, "client", Load.client());
        return fields;
    }

    private static void add(Fields fields, String name, Load.Setting setting) {
        if (setting.ms() > 0) {
            fields.put(name + "_ms", setting.ms(), name + "=" + setting);
            if (setting.jitter() > 0) {
                fields.hidden(name + "_jitter", setting.jitter());
            }
        }
    }
}
