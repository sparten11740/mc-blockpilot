package dev.blockpilot.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.blockpilot.api.Event;
import dev.blockpilot.api.Fields;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionTest {
    private static Event log(String level) {
        return Event.of("log", new Fields().bare("level", level).bare("logger", "test").bare("message", "m"));
    }

    @Test
    void logEventsBelowTheLevelAreNotDelivered() {
        Session session = new Session(1);
        session.setLogLevel("warn");
        session.deliver(log("INFO"));
        session.deliver(log("ERROR"));
        assertEquals(List.of("! log ERROR test m"), session.drainEvents().stream().map(Event::text).toList());
    }

    @Test
    void aBurstIsCappedAndTheRestIsReportedWithTheNextReply() {
        Session session = new Session(1);
        session.setLogLevel("info");
        for (int index = 0; index < Session.LOG_EVENTS_PER_SECOND + 5; index++) {
            session.deliver(log("WARN"));
        }
        List<String> events = session.drainEvents().stream().map(Event::text).toList();
        assertEquals(Session.LOG_EVENTS_PER_SECOND + 1, events.size());
        assertEquals("! log_dropped 5", events.get(events.size() - 1));
        assertEquals(List.of(), session.drainEvents());
    }
}
