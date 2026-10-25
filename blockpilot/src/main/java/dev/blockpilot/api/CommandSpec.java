package dev.blockpilot.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Everything Blockpilot knows about one command: its grammar, whether it only reads (query) or acts (action), the
 * fields it reports, its help, and its handler. Built-in and extension commands are described the same way.
 */
public final class CommandSpec {
    public enum Kind { QUERY, ACTION }

    @FunctionalInterface
    public interface QueryHandler {
        Result run(CommandContext context, Args args) throws CommandException;
    }

    @FunctionalInterface
    public interface ActionHandler {
        Action start(CommandContext context, Args args) throws CommandException;
    }

    private final String name;
    private final Kind kind;
    private String group = "misc";
    private final List<Form> forms = new ArrayList<>();
    private final Map<String, String> options = new LinkedHashMap<>();
    private final Map<String, String> fields = new LinkedHashMap<>();
    private String help = "";
    private String example;
    private boolean diffable;
    private boolean needsWorld;
    private boolean needsServer;
    private boolean onServer;
    private QueryHandler query;
    private ActionHandler action;

    private CommandSpec(String name, Kind kind) {
        this.name = Objects.requireNonNull(name);
        this.kind = kind;
    }

    /** A read-only command: runs between ticks and never changes game state. */
    public static CommandSpec query(String name, QueryHandler handler) {
        CommandSpec spec = new CommandSpec(name, Kind.QUERY);
        spec.query = handler;
        return spec;
    }

    /** A command that acts on the game; runs through the shared action queue. */
    public static CommandSpec action(String name, ActionHandler handler) {
        CommandSpec spec = new CommandSpec(name, Kind.ACTION);
        spec.action = handler;
        return spec;
    }

    public CommandSpec group(String value) {
        group = value;
        return this;
    }

    private Form firstForm() {
        if (forms.isEmpty()) {
            forms.add(new Form());
        }
        return forms.get(0);
    }

    /** Adds a slot to the command's first form; see {@link Form#arg}. */
    public CommandSpec arg(String argName, ArgType type, ArgType... more) {
        firstForm().arg(argName, type, more);
        return this;
    }

    public CommandSpec optionalArg(String argName, ArgType type, ArgType... more) {
        firstForm().optionalArg(argName, type, more);
        return this;
    }

    /** Adds a keyword to the command's first form. */
    public CommandSpec choice(String argName, boolean optional, String... words) {
        if (optional) {
            firstForm().optionalKeyword(argName, words);
        } else {
            firstForm().keyword(argName, words);
        }
        return this;
    }

    /** Adds another way to write the command; calls are matched against the forms in order. */
    public CommandSpec form(Consumer<Form> build) {
        Form form = new Form();
        build.accept(form);
        forms.add(form);
        return this;
    }

    /**
     * A {@code name=value} option, with what it means for help. A name ending in {@code :*}, like {@code rule:*},
     * declares a family: {@code rule:<name>=<value>} for any identifier name.
     */
    public CommandSpec option(String optionName, String description) {
        options.put(optionName, description);
        return this;
    }

    /** A field the result reports, with what it means; {@code wait … until} predicates refer to these. */
    public CommandSpec field(String fieldName, String description) {
        fields.put(fieldName, description);
        return this;
    }

    public CommandSpec help(String text) {
        help = text;
        return this;
    }

    public CommandSpec example(String text) {
        example = text;
        return this;
    }

    /** Repeated runs in a session report only changed rows. */
    public CommandSpec diffable() {
        diffable = true;
        return this;
    }

    /** Fails with {@code err not_in_world} outside a world. */
    public CommandSpec needsWorld() {
        needsWorld = true;
        return this;
    }

    /** Needs an integrated server whose world allows commands; fails with {@code no_server} or {@code no_cheats}. */
    public CommandSpec needsServer() {
        needsServer = true;
        needsWorld = true;
        return this;
    }

    /** Runs on the integrated server thread instead of the client thread; implies {@link #needsServer}. */
    public CommandSpec onServer() {
        onServer = true;
        return needsServer();
    }

    public String name() {
        return name;
    }

    public Kind kind() {
        return kind;
    }

    public String group() {
        return group;
    }

    /** The forms in the order calls are matched; a command without arguments has one empty form. */
    public List<Form> forms() {
        return forms.isEmpty() ? List.of(new Form()) : Collections.unmodifiableList(forms);
    }

    public Map<String, String> options() {
        return Collections.unmodifiableMap(options);
    }

    public Map<String, String> fields() {
        return Collections.unmodifiableMap(fields);
    }

    public String help() {
        return help;
    }

    public String example() {
        return example;
    }

    public boolean isDiffable() {
        return diffable;
    }

    public boolean needsWorldLoaded() {
        return needsWorld;
    }

    public boolean needsIntegratedServer() {
        return needsServer;
    }

    public boolean runsOnServer() {
        return onServer;
    }

    public QueryHandler queryHandler() {
        return query;
    }

    public ActionHandler actionHandler() {
        return action;
    }

    /** Each form as help shows it, e.g. {@code goto <target> [near=…]}. */
    public List<String> usages(String qualifiedName) {
        StringBuilder optionUsage = new StringBuilder();
        for (String option : options.keySet()) {
            optionUsage.append(" [").append(option.endsWith(":*") ? option.replace(":*", ":<name>") : option)
                .append("=…]");
        }
        List<String> usages = new ArrayList<>();
        for (Form form : forms()) {
            String body = form.usage();
            usages.add(qualifiedName + (body.isEmpty() ? "" : " " + body) + optionUsage);
        }
        return usages;
    }
}
