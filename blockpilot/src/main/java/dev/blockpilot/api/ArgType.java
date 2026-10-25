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
    ID("id", "an id like stone or create:cogwheel", Lexicon::isId),
    TAG("tag", "a tag like #logs", Lexicon::isTag),
    PATTERN("pattern", "a pattern like *ore*", Lexicon::isPattern),
    WORD("word", "a word", Lexicon::isWord),
    /** The rest of the step as written; only as a form's last slot. */
    TEXT("text", "text", token -> false),
    /** One of the slot's words. */
    CHOICE("choice", "one of the listed words", token -> false);

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

    /** Whether a token is of this type; {@link #TEXT} and {@link #CHOICE} are matched by their slot. */
    public boolean matches(String token) {
        return matcher.test(token);
    }
}
