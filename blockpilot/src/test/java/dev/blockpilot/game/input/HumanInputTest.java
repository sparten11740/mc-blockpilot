package dev.blockpilot.game.input;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** How an interrupt names the input that caused it. */
class HumanInputTest {
    @Test
    void eachKindOfInputHasItsFields() {
        assertEquals("input=key key=left_shift", HumanInput.keyInput("left_shift").text());
        assertEquals("input=button button=right", HumanInput.buttonInput(KeyNames.buttonNumber("right")).text());
        assertEquals("input=move dx=31 dy=-5", HumanInput.moveInput(30.6, -4.6).text());
    }
}
