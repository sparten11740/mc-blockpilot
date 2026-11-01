package dev.blockpilot.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** {@link ArgType#hides} and {@link ArgType#overlaps} agree with what the recognizers accept. */
class ArgTypeTest {
    private static final List<ArgType> TOKEN_TYPES = Arrays.stream(ArgType.values()).filter(ArgType::isToken)
        .toList();
    private static final List<String> TOKENS = tokens();

    private static List<String> tokens() {
        List<String> tokens = new ArrayList<>(List.of("5", "-5", "5.5", "0", "x6", "e5", "w3", "s5", "20t", "1.5s",
            "0.5", "1,2", "1,2,3", "~1,2,3", "stone", "minecraft:stone", "#logs", "*ore*", "full", "a:b",
            "0f3a9c2e-1b2c-4d5e-8f90-a1b2c3d4e5f6", "0F3A9C2E-1B2C-4D5E-8F90-A1B2C3D4E5F6", "Stone", "a=b", "hp<5",
            "oak_log[axis=y]"));
        Random random = new Random(1703);
        String alphabet = "0123456789aestwxzAZ~,.-:#*/_=<>!";
        for (int index = 0; index < 30_000; index++) {
            StringBuilder token = new StringBuilder();
            for (int length = 1 + random.nextInt(6); length > 0; length--) {
                token.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            tokens.add(token.toString());
        }
        return tokens;
    }

    @Test
    void typesSharingATokenAreDeclaredToOverlap() {
        for (String token : TOKENS) {
            for (ArgType first : TOKEN_TYPES) {
                for (ArgType second : TOKEN_TYPES) {
                    if (first.matches(token) && second.matches(token)) {
                        assertTrue(first.overlaps(second), first + " and " + second + " both take " + token);
                    }
                }
            }
        }
    }

    @Test
    void aTypeHidesOnlyTypesWhoseTokensAreAllItsOwn() {
        for (ArgType first : TOKEN_TYPES) {
            for (ArgType second : TOKEN_TYPES) {
                if (first.hides(second)) {
                    assertTrue(first.overlaps(second));
                    TOKENS.stream().filter(second::matches).forEach(token ->
                        assertTrue(first.matches(token), first + " doesn't take the " + second + " " + token));
                }
            }
        }
    }

    @Test
    void everyDeclaredOverlapHasAToken() {
        for (ArgType first : TOKEN_TYPES) {
            for (ArgType second : TOKEN_TYPES) {
                if (first.overlaps(second)) {
                    assertEquals(first.overlaps(second), second.overlaps(first));
                    assertTrue(TOKENS.stream().anyMatch(token -> first.matches(token) && second.matches(token)),
                        "no token is both " + first + " and " + second);
                }
            }
        }
    }

    @Test
    void textAndKeywordsAreMatchedByTheirSlot() {
        assertThrows(UnsupportedOperationException.class, () -> ArgType.TEXT.matches("a"));
        assertThrows(UnsupportedOperationException.class, () -> ArgType.KEYWORD.matches("a"));
        assertFalse(ArgType.TEXT.overlaps(ArgType.TEXT));
        assertFalse(ArgType.WORD.hides(ArgType.KEYWORD));
        assertFalse(ArgType.KEYWORD.hides(ArgType.WORD));
        assertFalse(ArgType.WORD.hides(ArgType.WORD));
        assertEquals("seconds", ArgType.SECONDS.production());
        assertEquals("seconds like 30 or 0.5", ArgType.SECONDS.description());
    }
}
