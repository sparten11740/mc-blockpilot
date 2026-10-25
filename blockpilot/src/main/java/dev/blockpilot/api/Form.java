package dev.blockpilot.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** One way to write a command: keywords, slots and optional groups, in order. Free text can only come last. */
public final class Form {
    public sealed interface Element permits Slot, Group {
    }

    /** Optional parts taken together: when the first fits the next token, the rest must follow. */
    public record Group(List<Element> elements) implements Element {
        public Group {
            elements = List.copyOf(elements);
        }
    }

    private final List<Element> elements = new ArrayList<>();
    private boolean closedByText;

    public List<Element> elements() {
        return Collections.unmodifiableList(elements);
    }

    /** A keyword: one of the words, stored under the name. */
    public Form keyword(String name, String... words) {
        return add(new Slot(name, List.of(ArgType.CHOICE), List.of(words), false));
    }

    public Form optionalKeyword(String name, String... words) {
        return add(new Slot(name, List.of(ArgType.CHOICE), List.of(words), true));
    }

    /** A token of the first of the types it matches; {@link ArgType#TEXT} makes it free text. */
    public Form arg(String name, ArgType type, ArgType... more) {
        return add(slot(name, false, type, more));
    }

    public Form optionalArg(String name, ArgType type, ArgType... more) {
        return add(slot(name, true, type, more));
    }

    /** One of the words, or else a token of the first of the types it matches. */
    public Form arg(String name, List<String> words, ArgType type, ArgType... more) {
        return add(withWords(slot(name, false, type, more), words));
    }

    public Form optionalArg(String name, List<String> words, ArgType type, ArgType... more) {
        return add(withWords(slot(name, true, type, more), words));
    }

    private static Slot withWords(Slot slot, List<String> words) {
        if (words.isEmpty() || slot.isText()) {
            throw new IllegalArgumentException("words go with token types, not with free text");
        }
        List<ArgType> types = new ArrayList<>(List.of(ArgType.CHOICE));
        types.addAll(slot.types());
        return new Slot(slot.name(), types, words, slot.optional());
    }

    /** Free text: the rest of the step as written. */
    public Form text(String name) {
        return add(new Slot(name, List.of(ArgType.TEXT), List.of(), false));
    }

    public Form optionalText(String name) {
        return add(new Slot(name, List.of(ArgType.TEXT), List.of(), true));
    }

    /** An optional group; it can't hold free text. */
    public Form optional(Consumer<Form> group) {
        Form inner = new Form();
        group.accept(inner);
        if (inner.elements.isEmpty() || inner.closedByText) {
            throw new IllegalArgumentException("an optional group needs parts and can't hold free text");
        }
        return add(new Group(inner.elements));
    }

    private static Slot slot(String name, boolean optional, ArgType type, ArgType... more) {
        List<ArgType> types = new ArrayList<>(List.of(type));
        types.addAll(List.of(more));
        if (types.contains(ArgType.CHOICE)) {
            throw new IllegalArgumentException("keywords are declared with keyword(name, words…)");
        }
        if (types.contains(ArgType.TEXT) && types.size() > 1) {
            throw new IllegalArgumentException("free text can't be combined with other types");
        }
        return new Slot(name, types, List.of(), optional);
    }

    private Form add(Element element) {
        if (closedByText) {
            throw new IllegalStateException("free text must be a form's last slot");
        }
        if (element instanceof Slot slot && slot.isKeyword() && slot.words().isEmpty()) {
            throw new IllegalArgumentException("a keyword needs at least one word");
        }
        elements.add(element);
        closedByText = element instanceof Slot slot && slot.isText();
        return this;
    }

    /** As help shows it, without the command name: {@code at <pos> [look <target>]}. */
    public String usage() {
        return usage(elements);
    }

    private static String usage(List<Element> parts) {
        StringBuilder out = new StringBuilder();
        for (Element element : parts) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(switch (element) {
                case Slot slot -> slot.usage();
                case Group group -> "[" + usage(group.elements()) + "]";
            });
        }
        return out.toString();
    }
}
