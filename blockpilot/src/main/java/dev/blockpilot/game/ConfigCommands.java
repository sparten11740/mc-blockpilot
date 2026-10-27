package dev.blockpilot.game;

import dev.blockpilot.Blockpilot;
import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import dev.blockpilot.game.input.KeyNames;
import dev.blockpilot.game.observe.TextPattern;
import dev.blockpilot.platform.ClientPlatform;
import dev.blockpilot.platform.ModConfigs;
import dev.blockpilot.protocol.CommandRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;

/** {@code config}: reads and sets mods' config values in memory, and puts back what agents changed. */
public final class ConfigCommands {
    /** A value before the first agent change, with the entry to put it back through. */
    private record Original(ModConfigs.File file, ModConfigs.Entry entry, Object value) {
    }

    /** By mod, file and key; client thread only. */
    private static final Map<String, Original> ORIGINALS = new LinkedHashMap<>();

    /** One value and the file it is in. */
    private record Found(ModConfigs.File file, ModConfigs.Entry entry) {
    }

    public void register(CommandRegistry registry) {
        registry.register(CommandSpec.action("config", this::config).group("session")
            .form(form -> { })
            .form(form -> form.keyword("op", "reset"))
            // With the type first, so a type's word is never read as a key.
            .form(form -> form.arg("mod", ArgType.WORD).keyword("type", "client", "common", "server", "startup")
                .optionalArg("key", ArgType.WORD).optionalText("value"))
            .form(form -> form.arg("mod", ArgType.WORD).optionalArg("key", ArgType.WORD).optionalText("value"))
            .option("match", "keys to list, e.g. *light*", ArgType.PATTERN)
            .help("config: the mods with config files, their types and files; config <mod> [<type>] [match=]: the "
                + "values by their dotted keys as in the file; config <mod> [<type>] <key>: its value, default, what "
                + "it takes, restart=world|game when a change only takes effect after that restart, and the mod's "
                + "comment; config <mod> [<type>] <key> <value>: sets it in memory and tells the mod as a reload of "
                + "its file does, never writing the file; config reset: puts back what agents changed, as leave, "
                + "quitting, the agent-control key and shutdown do. The type may be left out when one of the mod's "
                + "files has the key. Values as in the file: true|false (or on|off), numbers, names, text, lists "
                + "separated by commas. A world-restart value takes effect at the next world load, which join and "
                + "snapshot restore keep it for; a game-restart value, or a server value that waits for a world "
                + "restart, fails with err needs_restart. Server configs need a world, setting them the integrated "
                + "server, and their changes last until the world closes. NeoForge configs only")
            .field("value", "the value now").field("was", "the value before the first agent change")
            .field("default", "the mod's default").field("takes", "what the key accepts")
            .field("restart", "world or game: the restart a change waits for before the mod reads it")
            .field("comment", "the mod's comment on the key").field("restored", "the values put back")
            .example("config neoforge client showLoadWarnings false"));
    }

    private Action config(CommandContext context, Args args) throws CommandException {
        ModConfigs configs = ClientPlatform.modConfigs();
        if (!configs.available()) {
            throw new CommandException("unsupported", "config needs NeoForge's config system, which "
                + ClientPlatform.loaderName() + " doesn't have");
        }
        if (args.has("op")) {
            List<String> restored = restoreAll();
            String text = "restored " + (restored.isEmpty() ? "nothing" : String.join(" ", restored));
            return () -> Result.ok(new Fields().put("restored", restored, text));
        }
        if (!args.has("mod")) {
            return () -> Result.ok().withRows(mods(configs.files()));
        }
        List<ModConfigs.File> files = files(configs, args.word("mod"), args.has("type") ? args.word("type") : null);
        if (!args.has("key")) {
            Pattern match = args.option("match") == null ? null : TextPattern.compile(args.option("match"));
            List<Row> rows = values(files, args.has("type"), match);
            return () -> Result.ok().withRows(rows);
        }
        Found found = find(files, args.word("key"));
        requireLoaded(found.file());
        if (args.has("value")) {
            set(found, args.text("value"));
        }
        Fields fields = details(found);
        return () -> Result.ok(fields);
    }

    private static List<Row> mods(List<ModConfigs.File> files) {
        Map<String, List<ModConfigs.File>> byMod = new LinkedHashMap<>();
        files.forEach(file -> byMod.computeIfAbsent(file.modId(), mod -> new ArrayList<>()).add(file));
        List<Row> rows = new ArrayList<>();
        byMod.forEach((mod, ofMod) -> {
            List<String> types = ofMod.stream().map(ModConfigs.File::type).distinct().toList();
            List<String> names = ofMod.stream().map(ModConfigs.File::fileName).toList();
            rows.add(Row.of(mod, new Fields().bare("mod", mod).put("types", types, String.join(" ", types))
                .put("files", names, "files=" + String.join(",", names))));
        });
        return rows;
    }

    /** The mod's files, of the type when one is named. */
    private static List<ModConfigs.File> files(ModConfigs configs, String mod, String type) throws CommandException {
        List<ModConfigs.File> all = configs.files();
        List<ModConfigs.File> ofMod = all.stream().filter(file -> file.modId().equals(mod)).toList();
        if (ofMod.isEmpty()) {
            List<String> mods = all.stream().map(ModConfigs.File::modId).distinct().toList();
            throw new CommandException("syntax", "no mod named " + mod + " has config files; closest: "
                + String.join(" ", KeyNames.closest(mod, mods, 3)) + "; config lists them");
        }
        if (type == null) {
            return ofMod;
        }
        List<ModConfigs.File> ofType = ofMod.stream().filter(file -> file.type().equals(type)).toList();
        if (ofType.isEmpty()) {
            throw new CommandException("syntax", mod + " has no " + type + " config; it has "
                + String.join(" ", ofMod.stream().map(ModConfigs.File::type).distinct().toList()));
        }
        return ofType;
    }

    /** A listing leaves out files the loader holds no values for, unless their type was named. */
    private static List<Row> values(List<ModConfigs.File> files, boolean typeNamed, Pattern match)
        throws CommandException {
        List<Row> rows = new ArrayList<>();
        for (ModConfigs.File file : files) {
            if (!file.loaded()) {
                if (typeNamed) {
                    requireLoaded(file);
                }
                continue;
            }
            for (ModConfigs.Entry entry : file.entries()) {
                if (match == null || match.matcher(entry.key()).matches()) {
                    rows.add(Row.of(file.type() + "." + entry.key(), value(new Found(file, entry))));
                }
            }
        }
        return rows;
    }

    private static Found find(List<ModConfigs.File> files, String key) throws CommandException {
        List<Found> found = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (ModConfigs.File file : files) {
            for (ModConfigs.Entry entry : file.entries()) {
                keys.add(entry.key());
                if (entry.key().equals(key)) {
                    found.add(new Found(file, entry));
                }
            }
        }
        if (found.isEmpty()) {
            throw new CommandException("syntax", "no key " + key + "; closest: "
                + String.join(" ", KeyNames.closest(key, keys, 3)));
        }
        if (found.size() > 1) {
            throw new CommandException("syntax", key + " is in several config files ("
                + String.join(" ", found.stream().map(each -> each.file().type() + ":" + each.file().fileName())
                    .toList()) + "); name the type");
        }
        return found.get(0);
    }

    /** A server config is loaded only with a world: from the integrated server, or the copy a server sent. */
    private static void requireLoaded(ModConfigs.File file) throws CommandException {
        if (!file.loaded()) {
            throw new CommandException("not_in_world", file.type() + " config " + file.fileName()
                + " is loaded only in a world");
        }
    }

    private static void set(Found found, String text) throws CommandException {
        ModConfigs.File file = found.file();
        ModConfigs.Entry entry = found.entry();
        if (file.type().equals("server") && Minecraft.getInstance().getSingleplayerServer() == null) {
            throw new CommandException("no_server", "a server config can only be set on the integrated server; "
                + "this client holds the copy the server sent");
        }
        String restart = entry.restart();
        if (restart.equals("game") || restart.equals("world") && file.type().equals("server")) {
            // An unwritten change can't outlive a game restart, and a world load reads a server config's file again.
            String where = file.type().equals("server") ? "the world's serverconfig/" + file.fileName()
                : "config/" + file.fileName();
            throw new CommandException("needs_restart", entry.key() + " takes effect only after a " + restart
                + " restart, which reads the file again; edit " + where + " instead");
        }
        Object value = ConfigValues.parse(text, entry.defaultValue(), entry.value());
        if (value == null || !entry.accepts(value)) {
            throw new CommandException("syntax", entry.key() + " takes " + takes(entry) + ", not " + text);
        }
        ORIGINALS.putIfAbsent(id(file, entry), new Original(file, entry, entry.value()));
        entry.set(value);
    }

    private static String takes(ModConfigs.Entry entry) {
        return ConfigValues.takes(entry.defaultValue(), entry.value(), entry.min(), entry.max(), entry::accepts);
    }

    private static String id(ModConfigs.File file, ModConfigs.Entry entry) {
        return file.modId() + "/" + file.fileName() + "/" + entry.key();
    }

    /** Type, key, value, and the value before the first agent change when it differs. */
    private static Fields value(Found found) {
        ModConfigs.File file = found.file();
        ModConfigs.Entry entry = found.entry();
        String now = ConfigValues.text(entry.value());
        Fields fields = new Fields().bare("type", file.type()).bare("key", entry.key())
            .put("value", json(entry.value()), now);
        Original original = ORIGINALS.get(id(file, entry));
        if (original != null && !ConfigValues.text(original.value()).equals(now)) {
            fields.put("was", json(original.value()), "was " + ConfigValues.text(original.value()));
        }
        return fields;
    }

    private static Fields details(Found found) {
        ModConfigs.Entry entry = found.entry();
        Fields fields = value(found).put("default", json(entry.defaultValue()),
            "default " + ConfigValues.text(entry.defaultValue())).put("takes", takes(entry), "takes " + takes(entry));
        if (!entry.restart().equals("none")) {
            fields.put("restart", entry.restart());
        }
        String comment = entry.comment();
        if (comment != null && !comment.isBlank()) {
            String line = comment.strip().replaceAll("\\s*\\R\\s*", " ");
            fields.put("comment", line, "# " + line);
        }
        return fields.hidden("file", found.file().fileName());
    }

    /** JSON keeps switches and numbers as they are; names and lists of them as text. */
    private static Object json(Object value) {
        return switch (value) {
            case null -> null;
            case Enum<?> constant -> constant.name();
            case List<?> list -> list.stream().map(ConfigCommands::json).toList();
            default -> value;
        };
    }

    /** Puts back every value agents changed, telling each mod; returns them as {@code <mod>:<key>}. */
    public static List<String> restoreAll() {
        List<String> restored = new ArrayList<>();
        ORIGINALS.values().forEach(original -> {
            String name = original.file().modId() + ":" + original.entry().key();
            try {
                original.entry().set(original.value());
                restored.add(name);
            } catch (RuntimeException e) {
                // A mod's reload handler failing must not keep the other values, or shutdown, from going on.
                Blockpilot.LOGGER.warn("Could not put back the config value {}", name, e);
            }
        });
        ORIGINALS.clear();
        return restored;
    }
}
