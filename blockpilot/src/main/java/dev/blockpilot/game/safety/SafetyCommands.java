package dev.blockpilot.game.safety;

import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import dev.blockpilot.protocol.CommandRegistry;
import java.util.List;

/** {@code trust}: whom agents take words from. It only reads: the list is the config file's alone. */
public final class SafetyCommands {
    private final Trust trust;

    public SafetyCommands(Trust trust) {
        this.trust = trust;
    }

    public void register(CommandRegistry registry) {
        registry.register(CommandSpec.query("trust", (context, args) -> trust()).group("talk")
            .help("the players whose chat reaches agents with its text, each online or offline; they are set in "
                + "trustedPlayers in config/blockpilot/config.json only. unverified: the server doesn't check "
                + "accounts, so nobody is trusted there; trust_unverified: a development run trusts the list there all "
                + "the same; dangerously_allow_all: every safety check is off and everyone is trusted")
            .example("trust"));
    }

    Result trust() {
        Fields fields = new Fields().put("count", trust.names().size());
        if (trust.unverified()) {
            fields.put("unverified", true, "unverified");
        }
        if (trust.trustUnverified()) {
            fields.put("trust_unverified", true, "trust_unverified");
        }
        if (trust.allowAll()) {
            fields.put("dangerously_allow_all", true, "dangerously_allow_all");
        }
        List<Row> rows = trust.names().stream().map(name -> {
            boolean online = trust.online(name);
            return Row.of(name, new Fields().bare("name", name).put("online", online, online ? "online" : "offline"));
        }).toList();
        return Result.ok(fields).withRows(rows);
    }
}
