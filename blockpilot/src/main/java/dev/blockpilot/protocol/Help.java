package dev.blockpilot.protocol;

import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import dev.blockpilot.api.CommandSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The in-band reference, generated from the registry so it can't drift from the commands themselves. */
public final class Help {
    private final CommandRegistry registry;

    public Help(CommandRegistry registry) {
        this.registry = registry;
    }

    public Result answer(String topic) {
        if (topic == null || topic.isBlank()) {
            return overview();
        }
        if (topic.equals("errors")) {
            List<Row> rows = new ArrayList<>();
            Errors.CODES.forEach((code, meaning) -> rows.add(Row.of(code, new Fields().bare("code", code)
                .put("meaning", meaning, "— " + meaning))));
            return Result.ok().withRows(rows);
        }
        // "help sample:" always means the namespace, even when a command has the same name.
        boolean namespaceOnly = topic.endsWith(":");
        String name = namespaceOnly ? topic.substring(0, topic.length() - 1) : topic;
        CommandRegistry.Entry entry = namespaceOnly ? null : registry.resolve(name);
        if (entry != null) {
            return command(entry);
        }
        List<CommandRegistry.Entry> group = new ArrayList<>(registry.namespace(name));
        if (group.isEmpty()) {
            for (CommandRegistry.Entry candidate : registry.all()) {
                if (candidate.namespace().isEmpty() && candidate.spec().group().equals(name)) {
                    group.add(candidate);
                }
            }
        }
        if (group.isEmpty()) {
            return Result.error("syntax", "no help topic " + topic + "; try help");
        }
        List<Row> rows = new ArrayList<>();
        for (CommandRegistry.Entry member : group) {
            rows.add(Row.of(member.qualifiedName(), new Fields()
                .bare("usage", member.spec().usage(member.qualifiedName()))
                .put("help", member.spec().help(), member.spec().help().isEmpty() ? "" : "— " + member.spec().help())));
        }
        return Result.ok().withRows(rows);
    }

    private Result overview() {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (CommandRegistry.Entry entry : registry.all()) {
            if (entry.namespace().isEmpty()) {
                groups.computeIfAbsent(entry.spec().group(), key -> new ArrayList<>()).add(entry.qualifiedName());
            }
        }
        List<Row> rows = new ArrayList<>();
        groups.forEach((group, names) -> rows.add(Row.of(group, new Fields().put("group", group, group + ":")
            .put("commands", names, String.join(" ", names)))));
        for (String namespace : registry.namespaces()) {
            List<String> names = registry.namespace(namespace).stream().map(CommandRegistry.Entry::qualifiedName)
                .toList();
            rows.add(Row.of(namespace, new Fields().put("group", namespace, namespace + " (extension):")
                .put("commands", names, String.join(" ", names))));
        }
        rows.add(Row.of("more", new Fields().bare("hint",
            "help <command|group|namespace:|errors>; scripts: cmd1; cmd2 (stops at the first err; ?cmd goes on past "
                + "its err); positions x,y,z or ~dx,dy,dz; entities e<id>")));
        return Result.ok().withRows(rows);
    }

    private Result command(CommandRegistry.Entry entry) {
        CommandSpec spec = entry.spec();
        List<Row> rows = new ArrayList<>();
        rows.add(Row.of("usage", new Fields().bare("usage", spec.usage(entry.qualifiedName()))));
        if (!spec.help().isEmpty()) {
            rows.add(Row.of("help", new Fields().bare("help", spec.help())));
        }
        rows.add(Row.of("kind", new Fields().put("kind", spec.kind().name().toLowerCase(),
            spec.kind() == CommandSpec.Kind.QUERY ? "query (read-only)" : "action (runs in the action queue)")));
        spec.options().forEach((name, meaning) ->
            rows.add(Row.of("option " + name, new Fields().put("option", name, name + "= — " + meaning))));
        if (!spec.fields().isEmpty()) {
            List<String> fields = new ArrayList<>();
            spec.fields().forEach((name, meaning) -> fields.add(name + " (" + meaning + ")"));
            rows.add(Row.of("fields", new Fields().put("fields", new ArrayList<>(spec.fields().keySet()),
                "fields: " + String.join(", ", fields))));
        }
        if (spec.example() != null) {
            rows.add(Row.of("example", new Fields().put("example", spec.example(), "e.g. " + spec.example())));
        }
        if (spec.needsIntegratedServer()) {
            rows.add(Row.of("needs", new Fields().bare("needs", "singleplayer world with cheats on")));
        }
        return Result.ok().withRows(rows);
    }

    /** The short reference in the MCP {@code run} tool description. */
    public static String cheatSheet() {
        return """
            Runs Blockpilot commands in the Minecraft client. Script: commands separated by ; (one result line each, \
            stops at the first err; ?cmd goes on past its own err). Positions x,y,z or ~dx,dy,dz (relative); \
            entities e<id>; counts x<n>; durations 20t / 3s; options name=value.
            Common: status; look [r]; find <block|#tag|entity> [r]; block <pos>; inv; screen; goto <pos|e<id>>; \
            mine <pos|block> [x<n>]; place <item> <pos>; use [pos|e<id>]; attack <e<id>>; select <item>; \
            craft <item> [x<n>]; chat <text>; cmd <command>; mouse down|up|click <button>; drag <button> <dx> <dy> [Nt]; \
            key press <key>; wait <Nt>; wait <query> until <field><op><value>; stop; fmt json|text.
            Use help, help <command> or help errors for the full reference.""";
    }
}
