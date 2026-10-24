package dev.blockpilot.api;

/** A game event delivered to sessions as {@code ! <name> <fields>} in text and as an object in JSON. */
public record Event(String name, Fields fields) {
    public static Event of(String name, Fields fields) {
        return new Event(name, fields);
    }

    public static Event of(String name) {
        return new Event(name, new Fields());
    }

    public String text() {
        String body = fields.text();
        return body.isEmpty() ? "! " + name : "! " + name + " " + body;
    }
}
