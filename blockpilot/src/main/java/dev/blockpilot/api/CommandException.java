package dev.blockpilot.api;

import java.util.Objects;

/** Thrown by a handler to fail its command with a stable error code, e.g. {@code no_item torch}. */
public final class CommandException extends Exception {
    private final String code;
    private final String argument;
    private final transient Fields fields;

    public CommandException(String code) {
        this(code, null);
    }

    public CommandException(String code, String argument) {
        this(code, argument, new Fields());
    }

    public CommandException(String code, String argument, Fields fields) {
        super(argument == null ? code : code + " " + argument, null, false, false);
        this.code = Objects.requireNonNull(code);
        this.argument = argument;
        this.fields = fields;
    }

    public String code() {
        return code;
    }

    public String argument() {
        return argument;
    }

    public Result toResult() {
        return Result.error(code, argument).with(fields);
    }
}
