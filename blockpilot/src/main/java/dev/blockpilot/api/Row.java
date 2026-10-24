package dev.blockpilot.api;

/**
 * One line of a list result. The key identifies the row between two runs of the same command, so repeated
 * observations can be reported as changes (added, removed, changed rows) instead of in full.
 */
public record Row(String key, Fields fields) {
    public static Row of(String key, Fields fields) {
        return new Row(key, fields);
    }

    public String text() {
        return fields.text();
    }
}
