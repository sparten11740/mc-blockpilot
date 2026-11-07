package dev.blockpilot.game.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.blockpilot.api.CommandException;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class SelectionTest {
    private static final List<String> TESTS = List.of("blockpilot_smoke:passes", "mymod:doors",
        "blockpilot_smoke:fails", "blockpilotsmoketests.passes");

    @Test
    void aNamespaceStarPicksItsTestsSorted() throws CommandException {
        assertEquals(List.of("blockpilot_smoke:fails", "blockpilot_smoke:passes"),
            Selection.choose(null, () -> TESTS, "blockpilot_smoke:*"));
    }

    @Test
    void anIdPicksItsTestAndAClassStarItsMethods() throws CommandException {
        assertEquals(List.of("mymod:doors"), Selection.choose(null, () -> TESTS, "mymod:doors"));
        assertEquals(List.of("blockpilotsmoketests.passes"),
            Selection.choose(null, () -> TESTS, "blockpilotsmoketests.*"));
    }

    @Test
    void nothingMatchingIsNoTest() {
        CommandException error = assertThrows(CommandException.class,
            () -> Selection.choose(null, () -> TESTS, "nothing_here:*"));
        assertEquals("no_test", error.code());
        assertEquals("nothing_here:*", error.argument());
    }

    @Test
    void withGameTestsOffItIsUnsupportedWhateverTheSelectorAndListsNothing() {
        Supplier<Collection<String>> listing = () -> {
            throw new AssertionError("listed the tests although GameTests are off");
        };
        for (String selector : List.of("*", "nothing_here:*")) {
            CommandException error = assertThrows(CommandException.class,
                () -> Selection.choose("enable them with -Dneoforge.enableGameTest=true", listing, selector));
            assertEquals("unsupported", error.code());
            assertEquals("enable them with -Dneoforge.enableGameTest=true", error.argument());
        }
    }
}
