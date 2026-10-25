package dev.blockpilot.protocol;

import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.Result;

/**
 * Fails the running command with a stable error code from code that can't throw {@link CommandException}, such as an
 * interface method far below the handler.
 */
public final class CommandFailure extends RuntimeException {
    private final String code;
    private final String argument;

    public CommandFailure(String code, String argument) {
        super(code + " " + argument, null, false, false);
        this.code = code;
        this.argument = argument;
    }

    public Result toResult() {
        return Result.error(code, argument);
    }
}
