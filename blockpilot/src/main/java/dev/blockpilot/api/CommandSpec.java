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
 * fields it reports, its help, and its handler. Built-in and extension commands are described the same way. A spec
 * is checked against the grammar and frozen when it is registered; from then on it can't change.
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

    /** A {@code name=value} option; the value slot's types check the value, and text takes any value. */
    public record Option(String name, String description, Slot value) {
        public Option {
            Objects.requireNonNull(description);
            Objects.requireNonNull(value);
            boolean family = name != null && name.endsWith(":*")
                && Lexicon.isIdentifier(name.substring(0, name.length() - 2));
            if (!family && (name == null || !Lexicon.isOptionName(name))) {
                throw new IllegalArgumentException("an option is named <identifier> or <identifier>:<identifier>, "
                    + "or <identifier>:* for a family, not " + name);
            }
        }

        public boolean isFamily() {
            return name.endsWith(":*");
        }

        /** As help writes the name: {@code near}, or {@code rule:<name>} for a family. */
        public String usageName() {
            return isFamily() ? name.replace(":*", ":<name>") : name;
        }

        /** What the value takes, for error messages: {@code a number} or {@code client|server}. */
        public String expectation() {
            return value.takes();
        }

        /** As help shows the value: {@code number}, {@code client|server}, or {@code …} for any value. */
        public String valueUsage() {
            if (value.isText()) {
                return "…";
            }
            List<String> parts = new ArrayList<>(value.words());
            value.types().stream().filter(ArgType::isToken).forEach(type -> parts.add(type.production()));
            return String.join("|", parts);
        }
    }

    /** The option every action takes: how long it may run before it fails with {@code err timeout}. */
    public static final Option TIMEOUT = new Option("timeout", "fail with err timeout after this long",
        new Slot("value", List.of(ArgType.DURATION, ArgType.SECONDS), List.of(), false));

    private final String name;
    private final Kind kind;
    private String group = "misc";
    private final List<Form> forms = new ArrayList<>();
    private boolean explicitForms;
    private final Map<String, Option> options = new LinkedHashMap<>();
    private final Map<String, String> fields = new LinkedHashMap<>();
    private String help = "";
    private String example;
    private boolean diffable;
    private boolean needsWorld;
    private boolean needsServer;
    private boolean onServer;
    private final QueryHandler query;
    private final ActionHandler action;
    private boolean frozen;

    private CommandSpec(String name, Kind kind, QueryHandler query, ActionHandler action) {
        if (name == null || !Lexicon.isName(name)) {
            throw new IllegalArgumentException("a command name is [a-z][a-z0-9_]*, not " + name);
        }
        this.name = name;
        this.kind = kind;
        this.query = query;
        this.action = action;
    }

    /** A read-only command: runs between ticks and never changes game state. */
    public static CommandSpec query(String name, QueryHandler handler) {
        return new CommandSpec(name, Kind.QUERY, Objects.requireNonNull(handler), null);
    }

    /** A command that acts on the game; runs through the shared action queue. */
    public static CommandSpec action(String name, ActionHandler handler) {
        return new CommandSpec(name, Kind.ACTION, null, Objects.requireNonNull(handler));
    }

    private CommandSpec change() {
        if (frozen) {
            throw new IllegalStateException(name + " is registered and can't change");
        }
        return this;
    }

    public CommandSpec group(String value) {
        change().group = Objects.requireNonNull(value);
        return this;
    }

    /** The one form of a command whose parts are declared on the spec itself. */
    private Form onlyForm() {
        change();
        if (explicitForms) {
            throw new IllegalStateException(name + " declares its forms with form(…); add the part there");
        }
        if (forms.isEmpty()) {
            forms.add(new Form());
        }
        return forms.get(0);
    }

    /** Adds a slot to the command's only form; see {@link Form#arg}. */
    public CommandSpec arg(String argName, ArgType type, ArgType... more) {
        onlyForm().arg(argName, type, more);
        return this;
    }

    public CommandSpec optionalArg(String argName, ArgType type, ArgType... more) {
        onlyForm().optionalArg(argName, type, more);
        return this;
    }

    /** Adds a keyword to the command's only form. */
    public CommandSpec keyword(String argName, String... words) {
        onlyForm().keyword(argName, words);
        return this;
    }

    public CommandSpec optionalKeyword(String argName, String... words) {
        onlyForm().optionalKeyword(argName, words);
        return this;
    }

    /**
     * Adds a way to write the command; calls are matched against the forms in order and the first that takes every
     * token wins, so declare a form before one that would also take its calls. A command with forms declares all
     * of them this way.
     */
    public CommandSpec form(Consumer<Form> build) {
        change();
        if (!explicitForms && !forms.isEmpty()) {
            throw new IllegalStateException(name + " has parts declared on the spec; declare every form with "
                + "form(…)");
        }
        explicitForms = true;
        Form form = new Form();
        build.accept(form);
        forms.add(form);
        return this;
    }

    /** An option taking any value, which the handler checks. */
    public CommandSpec option(String optionName, String description) {
        return option(new Option(optionName, description, value(optionName, List.of(ArgType.TEXT), List.of())));
    }

    /**
     * An option whose value is of the first of the types it matches, checked before the script runs. A name ending
     * in {@code :*}, like {@code rule:*}, declares a family: {@code rule:<name>=<value>} for any identifier name.
     */
    public CommandSpec option(String optionName, String description, ArgType type, ArgType... more) {
        List<ArgType> types = new ArrayList<>(List.of(type));
        types.addAll(List.of(more));
        return option(new Option(optionName, description, value(optionName, types, List.of())));
    }

    /** An option whose value is one of the words, or else of the first of the types it matches. */
    public CommandSpec option(String optionName, String description, List<String> words, ArgType... types) {
        List<ArgType> all = new ArrayList<>(List.of(ArgType.KEYWORD));
        all.addAll(List.of(types));
        return option(new Option(optionName, description, value(optionName, all, words)));
    }

    /** The slot an option's value is checked with; its problems are named after the option. */
    private static Slot value(String optionName, List<ArgType> types, List<String> words) {
        try {
            return new Slot("value", types, words, false);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(e.getMessage().replaceFirst("^slot value", optionName + "="), e);
        }
    }

    private CommandSpec option(Option option) {
        change();
        if (kind == Kind.ACTION && option.name().equals(TIMEOUT.name())) {
            throw new IllegalArgumentException(name + ": every action takes timeout= already");
        }
        if (options.putIfAbsent(option.name(), option) != null) {
            throw new IllegalArgumentException(name + " declares " + option.name() + "= twice");
        }
        return this;
    }

    /**
     * A field the result reports, with what it means; the predicates of {@code wait}, {@code assert} and {@code if}
     * refer to these.
     */
    public CommandSpec field(String fieldName, String description) {
        if (fieldName == null || !Lexicon.isIdentifier(fieldName)) {
            throw new IllegalArgumentException("a field is named by an identifier, not " + fieldName);
        }
        change().fields.put(fieldName, Objects.requireNonNull(description));
        return this;
    }

    public CommandSpec help(String text) {
        change().help = Objects.requireNonNull(text);
        return this;
    }

    public CommandSpec example(String text) {
        change().example = text;
        return this;
    }

    /** Repeated runs in a session report only changed rows. */
    public CommandSpec diffable() {
        change().diffable = true;
        return this;
    }

    /** Fails with {@code err not_in_world} outside a world. */
    public CommandSpec needsWorld() {
        change().needsWorld = true;
        return this;
    }

    /** Needs an integrated server whose world allows commands; fails with {@code no_server} or {@code no_cheats}. */
    public CommandSpec needsServer() {
        change().needsServer = true;
        needsWorld = true;
        return this;
    }

    /** Runs on the integrated server thread instead of the client thread; implies {@link #needsServer}. */
    public CommandSpec onServer() {
        change().onServer = true;
        return needsServer();
    }

    /**
     * Checks the forms against themselves and stops all changes; Blockpilot calls this when the command is
     * registered, so a declaration the grammar can't reach fails there.
     */
    public CommandSpec freeze() {
        if (!frozen) {
            if (forms.isEmpty()) {
                forms.add(new Form());
            }
            for (int index = 0; index < forms.size(); index++) {
                try {
                    forms.get(index).freeze();
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(name + " (form " + (index + 1) + "): " + e.getMessage(), e);
                }
            }
            frozen = true;
        }
        return this;
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

    /** The declared options; actions also take {@link #TIMEOUT}, which {@link #findOption} includes. */
    public Map<String, Option> options() {
        return Collections.unmodifiableMap(options);
    }

    /** The option a {@code name=value} token gives, through a family or the actions' timeout; null if none. */
    public Option findOption(String optionName) {
        Option declared = options.get(optionName);
        if (declared != null) {
            return declared;
        }
        int colon = optionName.indexOf(':');
        if (colon > 0) {
            return options.get(optionName.substring(0, colon) + ":*");
        }
        return kind == Kind.ACTION && optionName.equals(TIMEOUT.name()) ? TIMEOUT : null;
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

    /** Each form as help shows it, e.g. {@code goto <target> [near=number]}; {@code [full]} for a diffable one. */
    public List<String> usages(String qualifiedName) {
        StringBuilder optionUsage = new StringBuilder(diffable ? " [full]" : "");
        for (Option option : options.values()) {
            optionUsage.append(" [").append(option.usageName()).append('=').append(option.valueUsage()).append(']');
        }
        List<String> usages = new ArrayList<>();
        for (Form form : forms()) {
            String body = form.usage();
            usages.add(qualifiedName + (body.isEmpty() ? "" : " " + body) + optionUsage);
        }
        return usages;
    }
}
