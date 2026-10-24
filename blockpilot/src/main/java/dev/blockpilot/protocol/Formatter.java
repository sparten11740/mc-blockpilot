package dev.blockpilot.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.blockpilot.api.Duration;
import dev.blockpilot.api.EntityRef;
import dev.blockpilot.api.Event;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Numbers;
import dev.blockpilot.api.Position;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Renders replies as compact text (for models) or as one JSON object (for scripts). */
public final class Formatter {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();

    private Formatter() {
    }

    public static String render(Reply reply, Session.Format format) {
        return format == Session.Format.JSON ? json(reply) : text(reply);
    }

    /** One line per result (payload lines indented by two spaces), then one {@code !} line per event. */
    public static String text(Reply reply) {
        StringBuilder out = new StringBuilder();
        for (Result result : reply.results()) {
            if (!out.isEmpty()) {
                out.append('\n');
            }
            appendResult(out, result);
        }
        if (reply.id() != null) {
            out.insert(0, "#" + reply.id() + " ");
        }
        for (Event event : reply.events()) {
            out.append('\n').append(event.text());
        }
        return out.toString();
    }

    private static void appendResult(StringBuilder out, Result result) {
        String fields = result.fields().text();
        switch (result.status()) {
            case SKIP -> out.append("skip");
            case ERR -> {
                out.append("err ").append(result.code());
                if (result.argument() != null && !result.argument().isEmpty()) {
                    out.append(' ').append(result.argument());
                }
                if (!fields.isEmpty()) {
                    out.append(' ').append(fields);
                }
            }
            case OK -> {
                out.append("ok");
                Result.Diff diff = result.diff();
                if (diff != null && diff.isEmpty()) {
                    out.append(" same");
                }
                if (!fields.isEmpty()) {
                    out.append(' ').append(fields);
                }
            }
        }
        Result.Diff diff = result.diff();
        if (diff != null) {
            diff.added().forEach(row -> out.append("\n  + ").append(row.text()));
            diff.removed().forEach(key -> out.append("\n  - ").append(key));
            diff.changed().forEach(row -> out.append("\n  ~ ").append(row.text()));
        } else if (result.rows() != null) {
            if (result.rows().isEmpty() && result.isOk() && fields.isEmpty()) {
                out.append(" none");
            }
            result.rows().forEach(row -> out.append("\n  ").append(row.text()));
        }
        if (result.more() > 0) {
            out.append("\n  +").append(result.more()).append(" more");
        }
    }

    /** A single-line JSON object: {@code {"id":…,"results":[…],"events":[…]}}. */
    public static String json(Reply reply) {
        JsonObject root = new JsonObject();
        if (reply.id() != null) {
            root.addProperty("id", reply.id());
        }
        JsonArray results = new JsonArray();
        reply.results().forEach(result -> results.add(json(result)));
        root.add("results", results);
        JsonArray events = new JsonArray();
        reply.events().forEach(event -> events.add(json(event)));
        root.add("events", events);
        return GSON.toJson(root);
    }

    public static JsonObject json(Event event) {
        JsonObject object = new JsonObject();
        object.addProperty("event", event.name());
        addFields(object, event.fields());
        return object;
    }

    public static JsonObject json(Result result) {
        JsonObject object = new JsonObject();
        switch (result.status()) {
            case SKIP -> object.addProperty("skip", true);
            case ERR -> {
                object.addProperty("ok", false);
                object.addProperty("err", result.code());
                if (result.argument() != null) {
                    object.addProperty("arg", result.argument());
                }
            }
            case OK -> object.addProperty("ok", true);
        }
        addFields(object, result.fields());
        Result.Diff diff = result.diff();
        if (diff != null) {
            if (diff.isEmpty()) {
                object.addProperty("same", true);
            }
            object.add("added", rows(diff.added()));
            JsonArray removed = new JsonArray();
            diff.removed().forEach(removed::add);
            object.add("removed", removed);
            object.add("changed", rows(diff.changed()));
        } else if (result.rows() != null) {
            object.add("rows", rows(result.rows()));
        }
        if (result.more() > 0) {
            object.addProperty("more", result.more());
        }
        return object;
    }

    private static JsonArray rows(List<Row> rows) {
        JsonArray array = new JsonArray();
        for (Row row : rows) {
            JsonObject object = new JsonObject();
            object.addProperty("key", row.key());
            addFields(object, row.fields());
            array.add(object);
        }
        return array;
    }

    private static void addFields(JsonObject object, Fields fields) {
        for (Fields.Field field : fields.list()) {
            object.add(field.name(), value(field.value()));
        }
    }

    static JsonElement value(Object value) {
        if (value == null) {
            return JsonNull.INSTANCE;
        }
        if (value instanceof JsonElement element) {
            return element;
        }
        if (value instanceof Boolean bool) {
            return new JsonPrimitive(bool);
        }
        if (value instanceof Double || value instanceof Float) {
            double number = ((Number) value).doubleValue();
            return Double.isFinite(number) ? new JsonPrimitive(Numbers.round(number)) : new JsonPrimitive(
                String.valueOf(number));
        }
        if (value instanceof Number number) {
            return new JsonPrimitive(number);
        }
        if (value instanceof Position position) {
            if (position.relative()) {
                return new JsonPrimitive(position.toString());
            }
            JsonArray array = new JsonArray();
            array.add(Numbers.round(position.x()));
            array.add(Numbers.round(position.y()));
            array.add(Numbers.round(position.z()));
            return array;
        }
        if (value instanceof EntityRef || value instanceof CharSequence) {
            return new JsonPrimitive(value.toString());
        }
        if (value instanceof Duration duration) {
            return new JsonPrimitive(duration.ticks());
        }
        if (value instanceof Map<?, ?> map) {
            JsonObject object = new JsonObject();
            map.forEach((key, child) -> object.add(String.valueOf(key), value(child)));
            return object;
        }
        if (value instanceof Collection<?> collection) {
            JsonArray array = new JsonArray();
            collection.forEach(child -> array.add(value(child)));
            return array;
        }
        return new JsonPrimitive(value.toString());
    }
}
