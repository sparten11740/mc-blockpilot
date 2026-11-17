package dev.blockpilot.game.observe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShotListTest {
    private static final Set<String> FORMS = Set.of("at", "move");
    private final List<String> read = new ArrayList<>();

    private List<CameraTimeline.Shot> parse(String text) throws CommandException {
        return ShotList.parse(text, FORMS::contains, "at, move or hold", (form, shot) -> {
            read.add(form + ": " + shot);
            if (shot.contains("over 0t")) {
                throw new CommandException("syntax", "over takes 1t or more");
            }
            if (shot.contains("e999")) {
                throw new CommandException("no_entity", "e999");
            }
            return new CameraTimeline.Cue(() -> () -> Result.ok(), 0);
        });
    }

    private String refused(String text) {
        CommandException refused = assertThrows(CommandException.class, () -> parse(text));
        return refused.code() + " " + refused.argument();
    }

    @Test
    void formsAreReadWholeAndHoldsTakeTheirTicks() throws CommandException {
        List<CameraTimeline.Shot> shots = parse("at ~0,3,-6 look ~0,1,0|hold 10t |  move ~6,3,0 over 20t");
        assertEquals(List.of("at: at ~0,3,-6 look ~0,1,0", "move: move ~6,3,0 over 20t"), read);
        assertEquals(new CameraTimeline.Hold(10), shots.get(1));
        assertEquals(3, shots.size());
    }

    @Test
    void aBadHoldNamesItsShot() {
        assertEquals("syntax shot 2, hold: hold takes a duration, as hold 40t", refused("at ~0,2,0 | hold"));
        assertEquals("syntax shot 1, hold 2t 3t: hold takes a duration, as hold 40t", refused("hold 2t 3t"));
    }

    @Test
    void anEmptyShotIsRefused() {
        assertEquals("syntax shot 2 is empty", refused("at ~0,2,0 || hold 2t"));
        assertEquals("syntax shot 2 is empty", refused("at ~0,2,0 |"));
    }

    @Test
    void aShotThatIsntAFormSaysWhatOneMayBe() {
        assertEquals("syntax shot 2, jump ~1,2,3: a shot is at, move or hold", refused("at ~0,2,0 | jump ~1,2,3"));
        assertEquals(List.of("at: at ~0,2,0"), read);
    }

    @Test
    void aFormsSyntaxErrorNamesTheShotAndOthersStayAsTheyAre() {
        assertEquals("syntax shot 2, move ~1,0,0 over 0t: over takes 1t or more",
            refused("at ~0,2,0 | move ~1,0,0 over 0t"));
        assertEquals("no_entity e999", refused("hold 5t | at ~0,2,0 look e999"));
    }
}
