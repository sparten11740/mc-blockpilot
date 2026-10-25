package dev.blockpilot.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class LexerTest {
    private static List<String> texts(Lexer.Step step) {
        return step.tokens().stream().map(Lexer.Token::text).toList();
    }

    @Test
    void stepsEndAtSemicolonsAndLineBreaks() {
        List<Lexer.Step> steps = Lexer.steps("select iron_pickaxe; mine ~0,-1,0\nstatus\r\nlook;;  \t;");
        assertEquals(4, steps.size());
        assertEquals(List.of("select", "iron_pickaxe"), texts(steps.get(0)));
        assertEquals(List.of("mine", "~0,-1,0"), texts(steps.get(1)));
        assertEquals(List.of("status"), texts(steps.get(2)));
        assertEquals(List.of("look"), texts(steps.get(3)));
        assertEquals(List.of(), Lexer.steps(""));
    }

    @Test
    void columnsCountFromTheFirstNonBlankCharacter() {
        Lexer.Step step = Lexer.steps("status;  \t turn  90 up").get(1);
        assertEquals("turn  90 up", step.source());
        assertEquals(List.of(1, 7, 10), step.tokens().stream().map(Lexer.Token::column).toList());
        assertEquals(List.of(0, 6, 9), step.tokens().stream().map(Lexer.Token::offset).toList());
    }

    @Test
    void anEscapedSemicolonStaysInTheStep() {
        Lexer.Step step = Lexer.steps("chat hi\\; brb  ; status").get(0);
        assertEquals("chat hi; brb", step.source());
        assertEquals(List.of("chat", "hi;", "brb"), texts(step));
        // As written, the escape takes two columns.
        assertEquals(11, step.tokens().get(2).column());
        assertEquals(9, step.tokens().get(2).offset());
        // A backslash before an escaped semicolon is a backslash.
        assertEquals(List.of("\\;x"), texts(Lexer.steps("\\\\;x").get(0)));
    }

    @Test
    void onlySpacesAndTabsAreBlanks() {
        Lexer.Step step = Lexer.steps("chat 　\f  \t").get(0);
        assertEquals(List.of("chat", "　\f"), texts(step));
        assertEquals("chat 　\f", step.source());
        assertEquals(7, step.width());
    }

    @Test
    void aStepsWidthCountsEscapesAsWritten() {
        Lexer.Step step = Lexer.steps("  chat a\\;b  ").get(0);
        assertEquals("chat a;b", step.source());
        assertEquals(9, step.width());
    }

    @Test
    void everyTokenIsInItsStepsSource() {
        Random random = new Random(1703);
        String alphabet = "ab ;\\\t\n\r　\f?";
        for (int run = 0; run < 20_000; run++) {
            StringBuilder script = new StringBuilder();
            for (int length = random.nextInt(16); length > 0; length--) {
                script.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            for (Lexer.Step step : Lexer.steps(script.toString())) {
                Lexer.Token last = step.tokens().get(step.tokens().size() - 1);
                assertEquals(step.source().length(), last.offset() + last.text().length(), script.toString());
                for (Lexer.Token token : step.tokens()) {
                    assertTrue(step.source().startsWith(token.text(), token.offset()), script.toString());
                }
            }
        }
    }

    @Test
    void aBackslashOnItsOwnIsJustACharacter() {
        assertEquals(List.of("a\\b", "c\\"), texts(Lexer.steps("a\\b c\\").get(0)));
        assertEquals(List.of(List.of("a\\"), List.of("b")),
            Lexer.steps("a\\\nb").stream().map(LexerTest::texts).toList());
    }
}
