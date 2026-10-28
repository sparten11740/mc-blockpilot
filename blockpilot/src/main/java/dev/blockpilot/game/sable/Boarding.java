package dev.blockpilot.game.sable;

import dev.blockpilot.api.BlockpilotApi;
import dev.blockpilot.api.Fields;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Reports when the sub-level carrying the player changes: {@code ! sable:left <sub>} for the one they no longer stand on
 * or ride, then {@code ! sable:boarded <sub>} for the one they now do. The first look after joining a world is silent.
 * {@link SableSupport#tick} looks once per client tick.
 */
final class Boarding {
    static final Boarding CLIENT = new Boarding(
        (event, fields) -> BlockpilotApi.emit(SableSupport.NAMESPACE, event, fields));

    private final BiConsumer<String, Fields> emit;
    private boolean watching;
    private UUID id;
    /** Kept from the last look, for a sub-level that is gone by the time the player has left it. */
    private String name;

    Boarding(BiConsumer<String, Fields> emit) {
        this.emit = emit;
    }

    /** The sub-level carrying the player now, or null for none. */
    void update(UUID now, String nowName) {
        if (watching && id != null && !id.equals(now)) {
            emit.accept("left", fields(id, name));
        }
        if (watching && now != null && !now.equals(id)) {
            emit.accept("boarded", fields(now, nowName));
        }
        watching = true;
        id = now;
        name = nowName;
    }

    /** Out of a world: the next world's first look is silent. */
    void forget() {
        watching = false;
    }

    private static Fields fields(UUID id, String name) {
        return new Fields().bare("sub", name).hidden("id", id.toString());
    }
}
