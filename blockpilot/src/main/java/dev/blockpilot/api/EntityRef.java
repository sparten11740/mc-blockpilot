package dev.blockpilot.api;

/** An entity reference token, {@code e<id>}, where the id is the entity's network id. */
public record EntityRef(int id) {
    @Override
    public String toString() {
        return "e" + id;
    }

    /** Parses {@code e<id>}; returns null when the token is not an entity reference. */
    public static EntityRef parse(String token) {
        return Lexicon.isEntity(token) ? new EntityRef(Integer.parseInt(token.substring(1))) : null;
    }
}
