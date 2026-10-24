package dev.blockpilot.api;

/** An entity reference token, {@code e<id>}, where the id is the entity's network id. */
public record EntityRef(int id) {
    @Override
    public String toString() {
        return "e" + id;
    }

    /** Parses {@code e<id>}; returns null when the token is not an entity reference. */
    public static EntityRef parse(String token) {
        if (token.length() < 2 || token.charAt(0) != 'e') {
            return null;
        }
        try {
            return new EntityRef(Integer.parseInt(token.substring(1)));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
