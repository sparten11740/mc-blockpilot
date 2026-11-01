package dev.blockpilot.api;

import java.util.function.Predicate;

/** The slot types of the grammar: what one argument token may be. */
public enum ArgType {
    POSITION("position", "a position x,y,z or ~dx,dy,dz", Lexicon::isPosition),
    /** A point on the open screen in GUI units. */
    POINT("point", "a screen point x,y", Lexicon::isPoint),
    ENTITY("entity", "an entity e<id>", Lexicon::isEntity),
    WIDGET("widget", "a widget w<index>", Lexicon::isWidget),
    /** A slot of the open container screen. */
    MENU_SLOT("menu-slot", "a menu slot s<index>", Lexicon::isMenuSlot),
    UUID("uuid", "a uuid", Lexicon::isUuid),
    INT("integer", "a whole number", Lexicon::isInteger),
    NUMBER("number", "a number", Lexicon::isDecimal),
    COUNT("count", "a count x<n>", Lexicon::isCount),
    DURATION("duration", "a duration like 20t or 3s", Lexicon::isDuration),
    /** A plain number of seconds, as {@code timeout=30} takes it. */
    SECONDS("seconds", "seconds like 30 or 0.5", Lexicon::isSeconds),
    ID("id", "an id like stone or create:cogwheel", Lexicon::isId),
    TAG("tag", "a tag like #logs", Lexicon::isTag),
    PATTERN("pattern", "a pattern like *ore*", Lexicon::isPattern),
    BLOCK_STATE("block-state", "a block with states like oak_log[axis=y]", Lexicon::isBlockState),
    WORD("word", "a word", Lexicon::isWord),
    /** The rest of the step as written; only as a form's last slot. */
    TEXT("text", "text", null),
    /** One of the slot's words. */
    KEYWORD("keyword", "one of the listed words", null);

    private final String production;
    private final String description;
    private final Predicate<String> matcher;

    ArgType(String production, String description, Predicate<String> matcher) {
        this.production = production;
        this.description = description;
        this.matcher = matcher;
    }

    /** The grammar production for this type, e.g. {@code position}. */
    public String production() {
        return production;
    }

    public String description() {
        return description;
    }

    /** Whether the type is one token's shape; text and keywords are matched by their slot. */
    public boolean isToken() {
        return matcher != null;
    }

    public boolean matches(String token) {
        if (matcher == null) {
            throw new UnsupportedOperationException(production + " is matched by its slot, not by token");
        }
        return matcher.test(token);
    }

    /** Whether every token of the other type is also of this one, so written first this one takes them all. */
    public boolean hides(ArgType other) {
        return isToken() && other.isToken() && this != other
            && (this == WORD || this == NUMBER && (other == INT || other == SECONDS));
    }

    /** Whether some token is of both types; all other token types are disjoint. */
    public boolean overlaps(ArgType other) {
        return isToken() && other.isToken() && (this == other || hides(other) || other.hides(this)
            || this == INT && other == SECONDS || this == SECONDS && other == INT);
    }
}
