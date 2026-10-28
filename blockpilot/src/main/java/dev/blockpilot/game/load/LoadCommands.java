package dev.blockpilot.game.load;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import dev.blockpilot.mixin.ConnectionAccessor;
import dev.blockpilot.protocol.CommandRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/**
 * {@code load}: idle time added on purpose to the integrated server's ticks or the client's frames, and delay to the
 * client's connection.
 */
public final class LoadCommands {
    public void register(CommandRegistry registry) {
        registry.register(CommandSpec.action("load", LoadCommands::load).group("performance")
            .form(form -> form.optionalKeyword("op", "off"))
            .form(form -> form.keyword("target", "server", "client", "net").arg("ms", ArgType.INT))
            .option("jitter", "milliseconds each tick, frame or packet may stray from the load either way, up to the "
                + "load", ArgType.INT)
            .help("load server|client <ms> [jitter=<ms>] adds idle time to every integrated-server tick, counted in "
                + "its MSPT, or to every frame (0 to " + Load.MAX_MS + " ms); load net <ms> holds every packet on the "
                + "client's connection that long in each direction, keeping their order (0 to " + Load.MAX_NET_MS
                + " ms); 0 ends one, load off ends all, load reports what is set; it also ends on leaving the world, "
                + "with agent control off and on closing, and nothing is saved; a read of the server from the client "
                + "(side=server, a sample's server queries) waits for the server's tick, so under server load it "
                + "holds the client too")
            .field("server_ms", "idle time added to each server tick").field("server_jitter", "how far it strays")
            .field("client_ms", "idle time added to each frame").field("client_jitter", "how far it strays")
            .field("net_ms", "delay of each packet, each way").field("net_jitter", "how far it strays")
            .example("load server 60 jitter=20"));
    }

    private static Action load(CommandContext context, Args args) throws CommandException {
        if (args.has("target")) {
            String target = args.word("target");
            int ms = args.integer("ms", 0);
            int jitter = args.intOption("jitter", 0);
            int max = target.equals("net") ? Load.MAX_NET_MS : Load.MAX_MS;
            if (ms < 0 || ms > max) {
                throw new CommandException("syntax", "load " + target + " takes 0 to " + max + " ms, not " + ms);
            }
            if (jitter < 0 || jitter > ms) {
                throw new CommandException("syntax", "jitter= takes 0 to the load, " + ms + " ms, not " + jitter);
            }
            if (target.equals("server") && Minecraft.getInstance().getSingleplayerServer() == null) {
                throw new CommandException("no_server");
            }
            if (target.equals("net")) {
                ClientPacketListener connection = Minecraft.getInstance().getConnection();
                if (connection == null) {
                    throw new CommandException("not_in_world");
                }
                NetDelay.install(((ConnectionAccessor) connection.getConnection()).blockpilot$channel());
            }
            Load.set(target, new Load.Setting(ms, jitter));
        } else if (args.hasOption("jitter")) {
            throw new CommandException("syntax", "jitter= goes with a load: load server|client|net <ms> jitter=<ms>");
        } else if (args.has("op")) {
            Load.off();
        }
        Fields report = report();
        return () -> Result.ok(report);
    }

    /** What is set, as {@code server=40±10ms net=80ms}, leaving out what is off. */
    static Fields report() {
        Fields fields = new Fields();
        add(fields, "server", Load.server());
        add(fields, "client", Load.client());
        add(fields, "net", Load.net());
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
