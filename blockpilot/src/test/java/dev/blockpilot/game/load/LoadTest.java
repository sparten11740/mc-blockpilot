package dev.blockpilot.game.load;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.blockpilot.api.Fields;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LoadTest {
    @AfterEach
    void endLoad() {
        Load.off();
    }

    private static String text(Fields fields) {
        return fields.list().stream().map(Fields.Field::text).filter(text -> !text.isEmpty())
            .collect(Collectors.joining(" "));
    }

    @Test
    void drawsWithinTheJitterAroundTheLoad() {
        Load.Setting setting = new Load.Setting(30, 20);
        assertEquals(10_000_000, setting.draw(() -> 0));
        assertEquals(30_000_000, setting.draw(() -> 0.5));
        assertEquals(49_999_000, setting.draw(() -> 0.999975));
        assertEquals(30_000_000, new Load.Setting(30, 0).draw(() -> 0.9));
    }

    @Test
    void reportsWhatIsSetAndOffEndsIt() {
        Load.set("server", new Load.Setting(40, 10));
        Load.set("client", new Load.Setting(5, 0));
        Fields report = LoadCommands.report();
        assertEquals("server=40±10ms client=5ms", text(report));
        assertEquals(10, report.get("server_jitter"));
        assertEquals("server=40±10ms client=5ms", Load.off());
        assertEquals("", text(LoadCommands.report()));
        assertEquals("", Load.off());
    }
}
