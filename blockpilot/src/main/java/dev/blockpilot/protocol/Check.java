package dev.blockpilot.protocol;

import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import java.util.List;

/**
 * {@code assert <query> <predicate>…} or {@code if <query> <predicate>… then <command>}: the read-only query, the
 * predicates of {@code wait … until} that end it, and for {@code if} the command to run when they hold.
 */
record Check(RawCommand query, Condition condition, RawCommand then) {
    static final String ASSERT_USAGE = "assert <query> <predicate> [and …]";
    static final String IF_USAGE = "if <query> <predicate> [and …] then <command>";

    static Check parse(RawCommand command, boolean conditional) throws CommandException {
        List<RawCommand.Token> tokens = command.tokens();
        String usage = conditional ? IF_USAGE : ASSERT_USAGE;
        int end = tokens.size();
        RawCommand then = null;
        if (conditional) {
            end = indexOf(tokens, "then");
            if (end < 0 || end == tokens.size() - 1) {
                throw new CommandException("syntax", usage);
            }
            String rest = command.restFrom(end + 1);
            then = RawCommand.of(rest);
            // A soft if makes its command soft too.
            if (command.soft() && !then.soft()) {
                then = RawCommand.of("?" + rest);
            }
        }
        int first = end;
        while (first > 0 && Condition.isPredicate(tokens.get(first - 1).text())) {
            first--;
        }
        if (first == end || first == 0) {
            throw new CommandException("syntax", usage);
        }
        RawCommand query = RawCommand.of(command.argumentText().substring(0, tokens.get(first).start()));
        List<String> predicates = tokens.subList(first, end).stream().map(RawCommand.Token::text).toList();
        return new Check(query, Condition.parse(predicates), then);
    }

    private static int indexOf(List<RawCommand.Token> tokens, String word) {
        for (int index = 0; index < tokens.size(); index++) {
            if (tokens.get(index).text().equals(word)) {
                return index;
            }
        }
        return -1;
    }

    /** True when this is an {@code if} whose predicates hold, so its command runs in its place. */
    boolean runsCommand(Result result) {
        return then != null && condition.holds(result);
    }

    /** The line of an assert, or of an if that does not run its command. */
    Result verdict(Result result) {
        Fields observed = condition.observed(result);
        if (then != null) {
            Fields fields = new Fields().put("if", false, "false");
            if (!result.isOk()) {
                fields.put("error", result.code(), "error=" + result.code());
            }
            return Result.ok(fields).with(observed);
        }
        if (condition.holds(result)) {
            return Result.ok(observed);
        }
        return Result.error("assert_failed", result.isOk() ? null : query.name() + " " + result.code()).with(observed);
    }
}
