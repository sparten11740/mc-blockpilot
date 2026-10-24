package dev.blockpilot.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The structured result of one command: ok, err with a stable code, or skip; named fields; and optional rows. The
 * endpoint renders it as compact text or JSON, so handlers never build output strings themselves.
 */
public final class Result {
    public enum Status { OK, ERR, SKIP }

    /** Changes since the session's last run of a diffable command. */
    public record Diff(List<Row> added, List<String> removed, List<Row> changed) {
        public boolean isEmpty() {
            return added.isEmpty() && removed.isEmpty() && changed.isEmpty();
        }
    }

    private final Status status;
    private final String code;
    private final String argument;
    private final Fields fields;
    private final List<Row> rows;
    private final int more;
    private final Diff diff;

    private Result(Status status, String code, String argument, Fields fields, List<Row> rows, int more, Diff diff) {
        this.status = status;
        this.code = code;
        this.argument = argument;
        this.fields = fields;
        this.rows = rows;
        this.more = more;
        this.diff = diff;
    }

    public static Result ok() {
        return new Result(Status.OK, null, null, new Fields(), null, 0, null);
    }

    public static Result ok(Fields fields) {
        return new Result(Status.OK, null, null, fields, null, 0, null);
    }

    public static Result error(String code) {
        return error(code, null);
    }

    public static Result error(String code, String argument) {
        return new Result(Status.ERR, code, argument, new Fields(), null, 0, null);
    }

    public static Result skip() {
        return new Result(Status.SKIP, null, null, new Fields(), null, 0, null);
    }

    /** Adds the fields to this result (fluent; results are built once and then only read). */
    public Result with(Fields extra) {
        for (Fields.Field field : extra.list()) {
            fields.put(field.name(), field.value(), field.text());
        }
        return this;
    }

    /** A copy carrying these rows; {@code more} counts rows left out because of a cap. */
    public Result withRows(List<Row> newRows, int moreRows) {
        return new Result(status, code, argument, fields, new ArrayList<>(newRows), moreRows, null);
    }

    public Result withRows(List<Row> newRows) {
        return withRows(newRows, 0);
    }

    /** A copy that reports these changes instead of the full rows. */
    public Result withDiff(Diff changes) {
        return new Result(status, code, argument, fields, rows, more, changes);
    }

    public Status status() {
        return status;
    }

    public boolean isOk() {
        return status == Status.OK;
    }

    public String code() {
        return code;
    }

    public String argument() {
        return argument;
    }

    public Fields fields() {
        return fields;
    }

    /** The rows, or null when the command has none (distinct from an empty list). */
    public List<Row> rows() {
        return rows == null ? null : Collections.unmodifiableList(rows);
    }

    public int more() {
        return more;
    }

    public Diff diff() {
        return diff;
    }
}
