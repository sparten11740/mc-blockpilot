package dev.blockpilot.game.sable;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardingTest {
    private static final UUID SHIP = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RAFT = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final List<String> events = new ArrayList<>();
    private final Boarding boarding = new Boarding((event, fields) -> events.add(event + " " + fields.text()
        + " " + fields.get("id")));

    @Test
    void theFirstLookIsSilent() {
        boarding.update(SHIP, "ship");

        assertEquals(List.of(), events);
    }

    @Test
    void boardingAndLeavingAreReported() {
        boarding.update(null, null);
        boarding.update(SHIP, "ship");
        boarding.update(SHIP, "ship");
        boarding.update(null, null);

        assertEquals(List.of("boarded ship " + SHIP, "left ship " + SHIP), events);
    }

    @Test
    void movingAcrossLeavesTheOneBeforeBoardingTheNext() {
        boarding.update(SHIP, "ship");
        boarding.update(RAFT, "raft");

        assertEquals(List.of("left ship " + SHIP, "boarded raft " + RAFT), events);
    }

    @Test
    void aSubLevelThatIsGoneIsLeftUnderItsLastName() {
        boarding.update(SHIP, "ship");
        boarding.update(SHIP, "flagship");
        boarding.update(null, null);

        assertEquals(List.of("left flagship " + SHIP), events);
    }

    @Test
    void anotherWorldStartsSilent() {
        boarding.update(SHIP, "ship");
        boarding.forget();
        boarding.update(RAFT, "raft");

        assertEquals(List.of(), events);
    }
}
