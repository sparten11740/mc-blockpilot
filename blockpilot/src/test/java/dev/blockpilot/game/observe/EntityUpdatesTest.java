package dev.blockpilot.game.observe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.blockpilot.api.Fields;
import dev.blockpilot.game.observe.EntityUpdates.Kind;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EntityUpdatesTest {
    private static Fields fieldsOf(EntityUpdates updates, int entity) {
        Fields fields = new Fields();
        updates.addTo(fields, entity);
        return fields;
    }

    @Test
    void countsEachKindForItsEntity() {
        EntityUpdates updates = new EntityUpdates();
        updates.count(5, Kind.DATA);
        updates.count(5, Kind.MOVE);
        updates.count(5, Kind.MOVE);
        updates.count(5, Kind.MOVE);
        updates.count(7, Kind.TELEPORT);

        Fields fields = fieldsOf(updates, 5);
        assertEquals(Map.of("move", 3, "data", 1), fields.get("net"));
        assertEquals("net move3 data1", fields.text());
        assertEquals("net teleport1", fieldsOf(updates, 7).text());
    }

    @Test
    void leavesNetOutWithoutUpdates() {
        EntityUpdates updates = new EntityUpdates();
        updates.count(7, Kind.MOTION);

        assertNull(fieldsOf(updates, 5).get("net"));
    }

    @Test
    void clearingStartsTheNextTickAtNone() {
        EntityUpdates updates = new EntityUpdates();
        updates.count(5, Kind.MOVE);
        updates.clear();

        assertNull(fieldsOf(updates, 5).get("net"));
        updates.count(5, Kind.MOTION);
        assertEquals("net motion1", fieldsOf(updates, 5).text());
    }
}
