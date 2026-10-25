package dev.blockpilot.cli;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The result lines of a reply, text or JSON, as far as the exit code and the stderr summary need them. */
final class Results {
    record Result(String kind, String code, String argument) {
        boolean isErr() {
            return kind.equals("err");
        }
    }

    private Results() {
    }

    static List<Result> of(String reply) {
        return reply.startsWith("{") ? ofJson(reply) : ofText(reply);
    }

    /** Rows are indented and events start with {@code !}; a request id ({@code #<id>}) leads the line. */
    private static List<Result> ofText(String reply) {
        List<Result> results = new ArrayList<>();
        for (String line : reply.split("\n")) {
            if (line.isEmpty() || line.startsWith(" ") || line.startsWith("!")) {
                continue;
            }
            String[] words = withoutId(line).split(" ", 3);
            results.add(new Result(words[0], words.length > 1 ? words[1] : "", words.length > 2 ? words[2] : ""));
        }
        return results;
    }

    private static List<Result> ofJson(String reply) {
        List<Result> results = new ArrayList<>();
        for (Object element : resultList(reply)) {
            Map<?, ?> result = element instanceof Map<?, ?> object ? object : Map.of();
            if (Boolean.TRUE.equals(result.get("skip"))) {
                results.add(new Result("skip", "", ""));
            } else if (Boolean.TRUE.equals(result.get("ok"))) {
                results.add(new Result("ok", "", ""));
            } else {
                results.add(new Result("err", text(result.get("err")), text(result.get("arg"))));
            }
        }
        return results;
    }

    /** One stderr line when steps failed or were skipped, else null. */
    static String summary(List<Result> results) {
        List<Result> errors = results.stream().filter(Result::isErr).toList();
        long skips = results.stream().filter(result -> result.kind().equals("skip")).count();
        if (errors.isEmpty() && skips == 0) {
            return null;
        }
        String line = "blockpilot: " + errors.size() + " err, " + skips + " skip";
        // The parser refuses a script with a syntax error: that step answers "err syntax col <n>: …", every other
        // one skip.
        if (errors.size() == 1 && errors.get(0).code().equals("syntax") && errors.get(0).argument().startsWith("col ")
            && 1 + skips == results.size()) {
            return line + " (a syntax error, so nothing ran)";
        }
        return skips > 0 ? line + " (nothing after the error ran)" : line;
    }

    /** The game's pid when the reply holds a successful quit (text {@code ok pid=<n>}, JSON {@code pid}), else null. */
    static Long quitPid(String reply) {
        if (reply.startsWith("{")) {
            for (Object element : resultList(reply)) {
                if (element instanceof Map<?, ?> result && Boolean.TRUE.equals(result.get("ok"))
                    && result.get("pid") instanceof BigDecimal pid) {
                    return pid.longValue();
                }
            }
            return null;
        }
        for (String line : reply.split("\n")) {
            String[] words = withoutId(line.strip()).split("\\s+");
            if (words.length == 2 && words[0].equals("ok") && words[1].startsWith("pid=")) {
                return Long.parseLong(words[1].substring("pid=".length()));
            }
        }
        return null;
    }

    private static List<?> resultList(String reply) {
        return Json.parse(reply) instanceof Map<?, ?> object && object.get("results") instanceof List<?> list ? list
            : List.of();
    }

    private static String withoutId(String line) {
        if (!line.startsWith("#")) {
            return line;
        }
        int space = line.indexOf(' ');
        return space < 0 ? "" : line.substring(space + 1);
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
