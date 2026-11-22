package dev.blockpilot.game.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.blockpilot.api.CommandException;
import org.junit.jupiter.api.Test;

class FileNamesTest {
    @Test
    void aNameMayHoldAPlus() throws CommandException {
        assertEquals("own+bits", FileNames.checked("capture", "own+bits"));
        assertEquals("v1.4_base-2", FileNames.checked("capture", "v1.4_base-2"));
    }

    @Test
    void aBadNameIsNamedInTheError() {
        CommandException error = assertThrows(CommandException.class, () -> FileNames.checked("capture", "../x"));
        assertEquals("syntax", error.code());
        assertEquals("capture names use letters, digits, _ . + - (max 64, not starting with .), not ../x",
            error.argument());
    }

    @Test
    void hiddenAndOverlongNamesFail() {
        assertThrows(CommandException.class, () -> FileNames.checked("snapshot", ".base"));
        assertThrows(CommandException.class, () -> FileNames.checked("snapshot", "a".repeat(65)));
        assertThrows(CommandException.class, () -> FileNames.checked("snapshot", "two words"));
    }
}
