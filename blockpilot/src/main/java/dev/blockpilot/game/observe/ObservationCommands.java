package dev.blockpilot.game.observe;

import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Numbers;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import dev.blockpilot.game.Rel;
import dev.blockpilot.protocol.CommandRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Queries over the sound, message and particle logs. */
public final class ObservationCommands {

    public void register(CommandRegistry registry) {
        registry.register(CommandSpec.query("sounds", this::sounds).group("observe")
            .option("since", "first tick to include (use the tick of a previous reply)")
            .option("match", "sound id or pattern, e.g. aerolarge:* or *thunder*")
            .option("limit", "most recent n (default 50)")
            .help("sounds the client started: id, category, position, volume, pitch, tick (also when muted)")
            .field("tick", "current tick, for the next since=").field("count", "matching sounds")
            .example("sounds since=1200 match=*gear*"));
        registry.register(CommandSpec.query("messages", this::messages).group("observe")
            .option("since", "first tick to include").option("kind", "chat, actionbar, title or subtitle")
            .option("limit", "most recent n (default 50)")
            .help("chat, action-bar, title and subtitle messages with their tick").field("tick", "current tick")
            .field("count", "matching messages").example("messages kind=actionbar"));
        registry.register(CommandSpec.query("particles", this::particles).group("observe")
            .option("since", "first tick to include (default: the last 20 ticks)")
            .help("particles spawned per type over a window (the last 1200 ticks are kept)")
            .field("tick", "current tick").field("total", "all particles in the window").example("particles since=1200"));
    }

    private static long since(Args args, long fallback) throws CommandException {
        String value = args.option("since");
        try {
            return value == null ? fallback : Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new CommandException("syntax", "since= expects a tick number");
        }
    }

    private static Pattern glob(String pattern) {
        String id = pattern.contains(":") || pattern.startsWith("*") ? pattern : "minecraft:" + pattern;
        return Pattern.compile(("\\Q" + id + "\\E").replace("*", "\\E.*\\Q"));
    }

    private static <T> List<T> last(List<T> items, int limit) {
        return items.size() <= limit ? items : items.subList(items.size() - limit, items.size());
    }

    private Result sounds(CommandContext context, Args args) throws CommandException {
        long since = since(args, Long.MIN_VALUE);
        Pattern match = args.option("match") == null ? null : glob(args.option("match"));
        List<Observations.Sound> sounds = new ArrayList<>();
        for (Observations.Sound sound : Observations.sounds(since)) {
            String full = sound.id().contains(":") ? sound.id() : "minecraft:" + sound.id();
            if (match == null || match.matcher(full).matches()) {
                sounds.add(sound);
            }
        }
        List<Observations.Sound> shown = last(sounds, args.intOption("limit", 50));
        List<Row> rows = new ArrayList<>();
        for (Observations.Sound sound : shown) {
            Fields fields = new Fields().bare("id", sound.id()).bare("category", sound.category());
            if (sound.ui()) {
                fields.put("ui", true, "ui");
            } else {
                fields.bare("at", Rel.of(sound.position())).hidden("at_abs", Rel.absolute(sound.position()));
            }
            fields.put("volume", (double) sound.volume(), "vol" + Numbers.format(sound.volume()))
                .put("pitch", (double) sound.pitch(), "pitch" + Numbers.format(sound.pitch()))
                .put("tick", sound.tick(), "t" + sound.tick());
            rows.add(Row.of(sound.tick() + ":" + sound.id(), fields));
        }
        return Result.ok(new Fields().put("tick", Observations.tick(), "tick=" + Observations.tick())
            .put("count", sounds.size(), "count=" + sounds.size())).withRows(rows, sounds.size() - shown.size());
    }

    private Result messages(CommandContext context, Args args) throws CommandException {
        long since = since(args, Long.MIN_VALUE);
        String kind = args.option("kind");
        List<Observations.Message> messages = new ArrayList<>();
        for (Observations.Message message : Observations.messages(since)) {
            if (kind == null || kind.equals(message.kind())) {
                messages.add(message);
            }
        }
        List<Observations.Message> shown = last(messages, args.intOption("limit", 50));
        List<Row> rows = new ArrayList<>();
        for (Observations.Message message : shown) {
            rows.add(Row.of(message.tick() + ":" + message.kind(), new Fields().bare("kind", message.kind())
                .put("tick", message.tick(), "t" + message.tick()).bare("text", message.text())));
        }
        return Result.ok(new Fields().put("tick", Observations.tick(), "tick=" + Observations.tick())
            .put("count", messages.size(), "count=" + messages.size())).withRows(rows, messages.size() - shown.size());
    }

    private Result particles(CommandContext context, Args args) throws CommandException {
        long since = since(args, Observations.tick() - 20);
        Map<String, Integer> counts = Observations.particles(since);
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        int total = 0;
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : sorted) {
            total += entry.getValue();
            rows.add(Row.of(entry.getKey(), new Fields().bare("type", entry.getKey())
                .put("count", entry.getValue(), "x" + entry.getValue())));
        }
        return Result.ok(new Fields().put("tick", Observations.tick(), "tick=" + Observations.tick())
            .put("total", total, "total=" + total)).withRows(rows);
    }
}
