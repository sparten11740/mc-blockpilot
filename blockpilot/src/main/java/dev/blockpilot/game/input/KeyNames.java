package dev.blockpilot.game.input;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Key names as agents write them: the game's names without the keyboard prefix, words joined by {@code _}
 * ({@code key.keyboard.left.shift} is {@code left_shift}); {@code .} between words works too.
 */
public final class KeyNames {
    /** Mouse buttons as agents name them, in the order GLFW numbers them. */
    public static final List<String> BUTTONS = List.of("left", "right", "middle");
    private static final String KEYBOARD = "key.keyboard.";

    private KeyNames() {
    }

    /** What to look up for a name as written: the name itself, then with {@code _} between words as {@code .}. */
    public static List<String> spellings(String name) {
        String dotted = name.replace('_', '.');
        return dotted.equals(name) ? List.of(name) : List.of(name, dotted);
    }

    /** A game key name as agents write it; mouse buttons keep their full name, which is how they resolve. */
    public static String agentName(String gameName) {
        return gameName.startsWith(KEYBOARD) ? gameName.substring(KEYBOARD.length()).replace('.', '_') : gameName;
    }

    /** A mouse button by its GLFW number, as agents name it; past the third by the game's number (4 for the fourth). */
    public static String buttonName(int button) {
        return button >= 0 && button < BUTTONS.size() ? BUTTONS.get(button) : String.valueOf(button + 1);
    }

    /** The names closest to the wanted one by edit distance, nearest first, ties in alphabetical order. */
    public static List<String> closest(String wanted, Collection<String> names, int count) {
        return names.stream().distinct()
            .sorted(Comparator.comparingInt((String name) -> distance(wanted, name)).thenComparing(name -> name))
            .limit(count).toList();
    }

    static int distance(String from, String to) {
        int[] previous = new int[to.length() + 1];
        int[] current = new int[to.length() + 1];
        for (int column = 0; column <= to.length(); column++) {
            previous[column] = column;
        }
        for (int row = 1; row <= from.length(); row++) {
            current[0] = row;
            for (int column = 1; column <= to.length(); column++) {
                int substitution = previous[column - 1] + (from.charAt(row - 1) == to.charAt(column - 1) ? 0 : 1);
                current[column] = Math.min(substitution, Math.min(previous[column], current[column - 1]) + 1);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[to.length()];
    }
}
