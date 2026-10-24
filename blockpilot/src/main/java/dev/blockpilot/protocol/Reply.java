package dev.blockpilot.protocol;

import dev.blockpilot.api.Event;
import dev.blockpilot.api.Result;
import java.util.List;

/** The answer to one request: one result per command, then the session's pending events. */
public record Reply(String id, List<Result> results, List<Event> events) {
    public boolean allOk() {
        return results.stream().allMatch(Result::isOk);
    }
}
