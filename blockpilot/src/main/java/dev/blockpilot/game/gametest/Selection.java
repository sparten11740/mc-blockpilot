package dev.blockpilot.game.gametest;

import dev.blockpilot.api.CommandException;
import dev.blockpilot.game.observe.TextPattern;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Which registered GameTests a selector picks, or why none can run. */
final class Selection {
    private Selection() {
    }

    /**
     * The ids the selector matches, sorted; {@code off} is why the loader runs no GameTests, or null. The ids are only
     * asked for once GameTests run, as listing them may register Blockpilot's own dev tests.
     */
    static List<String> choose(String off, Supplier<Collection<String>> ids, String selector) throws CommandException {
        if (off != null) {
            throw new CommandException("unsupported", off);
        }
        Pattern pattern = TextPattern.compile(selector);
        List<String> chosen = ids.get().stream().filter(id -> pattern.matcher(id).matches()).sorted().toList();
        if (chosen.isEmpty()) {
            throw new CommandException("no_test", selector);
        }
        return chosen;
    }
}
