package dev.blockpilot.game.safety;

import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import dev.blockpilot.protocol.AllowAllPolicy;
import dev.blockpilot.protocol.CommandRegistry;
import dev.blockpilot.protocol.Connection;
import dev.blockpilot.protocol.SafetyPolicy;
import java.util.List;
import java.util.function.Supplier;

/** {@code trust}: whom agents take words from. It only reads: the list is the config file's alone. */
public final class SafetyCommands {
    private final Supplier<SafetyPolicy> policy;
    private final Trust trust;
    private final boolean trustUnverified;

    public SafetyCommands(Supplier<SafetyPolicy> policy, Trust trust, boolean trustUnverified) {
        this.policy = policy;
        this.trust = trust;
        this.trustUnverified = trustUnverified;
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
        SafetyPolicy now = policy.get();
        Connection connection = trust.connection();
        List<String> names = now.trustedNames();
        Fields fields = new Fields().put("count", names.size());
        if (connection.unverified()) {
            fields.put("unverified", true, "unverified");
        }
        if (trustUnverified) {
            fields.put("trust_unverified", true, "trust_unverified");
        }
        if (now instanceof AllowAllPolicy) {
            fields.put("dangerously_allow_all", true, "dangerously_allow_all");
        }
        List<Row> rows = names.stream().map(name -> {
            boolean online = connection.online(name);
            return Row.of(name, new Fields().bare("name", name).put("online", online, online ? "online" : "offline"));
        }).toList();
        return Result.ok(fields).withRows(rows);
    }
}
