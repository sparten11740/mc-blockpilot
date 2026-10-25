package dev.blockpilot.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** One way to write a command: keywords, slots and optional groups, in order. Free text can only come last. */
public final class Form {
    public sealed interface Element permits Slot, Group {
    }

    /** Optional parts taken together: when the first, which is required, fits the next token, the rest must follow. */
    public record Group(List<Element> elements) implements Element {
        public Group {
            elements = List.copyOf(elements);
            if (elements.isEmpty()) {
                throw new IllegalArgumentException("an optional group needs parts");
            }
            if (!(elements.get(0) instanceof Slot first) || first.optional()) {
                throw new IllegalArgumentException("an optional group's first part can't be optional");
            }
            if (elements.stream().anyMatch(element -> element instanceof Slot slot && slot.isText())) {
                throw new IllegalArgumentException("an optional group can't hold free text");
            }
        }

        /** The part that decides whether the group is taken. */
        public Slot first() {
            return (Slot) elements.get(0);
        }
    }

    private final List<Element> elements = new ArrayList<>();
    private boolean closedByText;
    private boolean frozen;

    public List<Element> elements() {
        return Collections.unmodifiableList(elements);
    }

    /** A keyword: one of the words, stored under the name. */
    public Form keyword(String name, String... words) {
        return add(new Slot(name, List.of(ArgType.KEYWORD), List.of(words), false));
    }

    public Form optionalKeyword(String name, String... words) {
        return add(new Slot(name, List.of(ArgType.KEYWORD), List.of(words), true));
    }

    /** A token of the first of the types it matches; {@link ArgType#TEXT} makes it free text. */
    public Form arg(String name, ArgType type, ArgType... more) {
        return add(slot(name, false, List.of(), type, more));
    }

    public Form optionalArg(String name, ArgType type, ArgType... more) {
        return add(slot(name, true, List.of(), type, more));
    }

    /** One of the words, or else a token of the first of the types it matches. */
    public Form arg(String name, List<String> words, ArgType type, ArgType... more) {
        return add(slot(name, false, words, type, more));
    }

    public Form optionalArg(String name, List<String> words, ArgType type, ArgType... more) {
        return add(slot(name, true, words, type, more));
    }

    /** Free text: the rest of the step as written. */
    public Form text(String name) {
        return add(new Slot(name, List.of(ArgType.TEXT), List.of(), false));
    }

    public Form optionalText(String name) {
        return add(new Slot(name, List.of(ArgType.TEXT), List.of(), true));
    }

    /** An optional group; its first part is required (it decides whether the group is taken) and none is text. */
    public Form optional(Consumer<Form> group) {
        Form inner = new Form();
        group.accept(inner);
        return add(new Group(inner.elements));
    }

    private static Slot slot(String name, boolean optional, List<String> words, ArgType type, ArgType... more) {
        List<ArgType> types = new ArrayList<>();
        if (!words.isEmpty()) {
            types.add(ArgType.KEYWORD);
        }
        types.add(type);
        types.addAll(List.of(more));
        if (types.lastIndexOf(ArgType.KEYWORD) > 0 || words.isEmpty() && types.contains(ArgType.KEYWORD)) {
            throw new IllegalArgumentException("keywords are declared with keyword(name, words…)");
        }
        return new Slot(name, types, words, optional);
    }

    private Form add(Element element) {
        if (frozen) {
            throw new IllegalStateException("the form belongs to a registered command and can't change");
        }
        if (closedByText) {
            throw new IllegalStateException("free text must be a form's last slot");
        }
        elements.add(element);
        closedByText = element instanceof Slot slot && slot.isText();
        return this;
    }

    /**
     * Checks that every part can be reached and stops further changes: slot names are unique, and no optional part
     * could take a token that a part after it is meant for.
     */
    void freeze() {
        Set<String> names = new HashSet<>();
        checkNames(elements, names);
        checkOptionalParts(elements, List.of());
        frozen = true;
    }

    private static void checkNames(List<Element> parts, Set<String> names) {
        for (Element part : parts) {
            switch (part) {
                case Slot slot -> {
                    if (!names.add(slot.name())) {
                        throw new IllegalArgumentException("two slots are named " + slot.name());
                    }
                }
                case Group group -> checkNames(group.elements(), names);
            }
        }
    }

    /** {@code follow}: the slots that could take the token after these parts. */
    private static void checkOptionalParts(List<Element> parts, List<Slot> follow) {
        for (int index = 0; index < parts.size(); index++) {
            Element part = parts.get(index);
            List<Slot> next = first(parts.subList(index + 1, parts.size()), follow);
            if (part instanceof Group group) {
                checkOptionalParts(group.elements(), next);
            }
            if (isOptional(part)) {
                Slot lead = lead(part);
                for (Slot later : next) {
                    if (lead.overlaps(later)) {
                        throw new IllegalArgumentException("optional " + lead.usage() + " would take a token meant for "
                            + later.usage() + "; give them distinct types or group them as [<a> [b]]");
                    }
                }
            }
        }
    }

    /** The slots that could take the next token: up to the first required part, then what follows. */
    private static List<Slot> first(List<Element> parts, List<Slot> follow) {
        List<Slot> slots = new ArrayList<>();
        for (Element part : parts) {
            slots.add(lead(part));
            if (!isOptional(part)) {
                return slots;
            }
        }
        slots.addAll(follow);
        return slots;
    }

    private static Slot lead(Element part) {
        return switch (part) {
            case Slot slot -> slot;
            case Group group -> group.first();
        };
    }

    private static boolean isOptional(Element part) {
        return !(part instanceof Slot slot) || slot.optional();
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
