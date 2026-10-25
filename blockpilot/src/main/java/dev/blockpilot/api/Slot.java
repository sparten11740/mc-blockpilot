package dev.blockpilot.api;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One argument of a form: a keyword (type {@link ArgType#KEYWORD} with its words), free text (only last), or a token
 * of one or more types, the first matching one taken. The parsed value is stored under {@code name}.
 */
public record Slot(String name, List<ArgType> types, List<String> words, boolean optional) implements Form.Element {
    /** Words a keyword can't be: they end a construct's query, join predicates, or ask for every row. */
    public static final Set<String> RESERVED_WORDS = Set.of("ok", "until", "every", "then", "and", "full");

    public Slot {
        types = List.copyOf(types);
        words = List.copyOf(words);
        require(name != null && Lexicon.isIdentifier(name), "a slot name is an identifier, not " + name);
        require(!types.isEmpty() && Set.copyOf(types).size() == types.size(), "slot " + name
            + " needs one or more distinct types");
        require(!types.contains(ArgType.TEXT) || types.size() == 1, "slot " + name
            + ": free text can't be combined with other types");
        require(types.contains(ArgType.KEYWORD) == !words.isEmpty(), "slot " + name
            + ": words go with the keyword type, and a keyword needs at least one word");
        require(!types.contains(ArgType.KEYWORD) || types.get(0) == ArgType.KEYWORD, "slot " + name
            + ": the words come before the other types");
        require(Set.copyOf(words).size() == words.size(), "slot " + name + " lists a word twice");
        for (String word : words) {
            require(Lexicon.isWord(word) && !RESERVED_WORDS.contains(word), "slot " + name + ": " + word
                + " can't be a keyword (not a word, or one of " + String.join(" ", RESERVED_WORDS.stream().sorted()
                .toList()) + ")");
        }
        for (int later = 1; later < types.size(); later++) {
            for (int earlier = 0; earlier < later; earlier++) {
                require(!types.get(earlier).hides(types.get(later)), "slot " + name + ": "
                    + types.get(earlier).production() + " takes every " + types.get(later).production()
                    + ", so " + types.get(later).production() + " must come first");
            }
        }
    }

    private static void require(boolean condition, String problem) {
        if (!condition) {
            throw new IllegalArgumentException(problem);
        }
    }

    public boolean isText() {
        return types.contains(ArgType.TEXT);
    }

    /** Only words: a keyword, rather than a slot that also takes words. */
    public boolean isKeyword() {
        return types.equals(List.of(ArgType.KEYWORD));
    }

    /** The type the token is taken as, or null when it doesn't fit. Text isn't matched by token. */
    public ArgType match(String token) {
        if (isText()) {
            return null;
        }
        for (ArgType type : types) {
            if (type == ArgType.KEYWORD ? words.contains(token) : type.matches(token)) {
                return type;
            }
        }
        return null;
    }

    /** Whether some token could be taken by this slot and by the other one; text takes any and isn't compared. */
    boolean overlaps(Slot other) {
        if (isText() || other.isText()) {
            return false;
        }
        Set<String> shared = new HashSet<>(words);
        shared.retainAll(other.words);
        if (!shared.isEmpty() || words.stream().anyMatch(word -> other.match(word) != null)
            || other.words.stream().anyMatch(word -> match(word) != null)) {
            return true;
        }
        return types.stream().anyMatch(type -> other.types.stream().anyMatch(type::overlaps));
    }

    /** As help shows it: {@code <pos>}, {@code [radius]} or {@code down|up}. */
    public String usage() {
        if (isKeyword()) {
            String alternatives = String.join("|", words);
            return optional ? "[" + alternatives + "]" : alternatives;
        }
        return optional ? "[" + name + "]" : "<" + name + ">";
    }

    /** What the slot takes, for error messages: {@code <dy> (a number)}. */
    public String expectation() {
        return isKeyword() ? takes() : "<" + name + "> (" + takes() + ")";
    }

    /** {@code a number}, {@code down|up} or {@code head|offhand or a whole number}. */
    String takes() {
        StringBuilder out = new StringBuilder();
        for (int index = 0; index < types.size(); index++) {
            ArgType type = types.get(index);
            out.append(index == 0 ? "" : " or ").append(type == ArgType.KEYWORD ? String.join("|", words)
                : type.description());
        }
        return out.toString();
    }
}
