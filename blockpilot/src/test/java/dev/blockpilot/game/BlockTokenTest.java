package dev.blockpilot.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.blockpilot.api.CommandException;
import java.util.List;
import org.junit.jupiter.api.Test;

class BlockTokenTest {
    @Test
    void aTokenWithoutBracketsHasNoStates() throws CommandException {
        BlockToken parsed = BlockToken.of("#logs");
        assertEquals("#logs", parsed.block());
        assertTrue(parsed.states().isEmpty());
    }

    @Test
    void theStatesKeepTheirOrder() throws CommandException {
        BlockToken parsed = BlockToken.of("minecraft:oak_stairs[half=top,facing=east,waterlogged=false]");
        assertEquals("minecraft:oak_stairs", parsed.block());
        assertEquals(List.of("half", "facing", "waterlogged"), List.copyOf(parsed.states().keySet()));
        assertEquals("east", parsed.states().get("facing"));
    }

    @Test
    void aStateGivenTwiceIsRefused() {
        CommandException refused = assertThrows(CommandException.class, () -> BlockToken.of("oak_log[axis=x,axis=y]"));
        assertEquals("syntax", refused.code());
        assertTrue(refused.argument().contains("axis"), refused.argument());
    }

    @Test
    void malformedBracketsAreRefused() {
        for (String token : List.of("oak_log[]", "oak_log[axis]", "oak_log[axis=y", "oak_log[Axis=y]")) {
            assertEquals("syntax", assertThrows(CommandException.class, () -> BlockToken.of(token)).code(), token);
        }
    }
}
