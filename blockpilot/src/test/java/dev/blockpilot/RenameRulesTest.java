package dev.blockpilot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The rules in stonecutter.gradle.kts are plain text, which nothing else checks until a node fails to compile. */
class RenameRulesTest {
    private static final String TEXT = "\"((?:[^\"\\\\]|\\\\.)*)\"";
    private static final Pattern RULE =
        Pattern.compile("rename\\(\\s*" + TEXT + ",\\s*" + TEXT + ",\\s*" + TEXT + "\\s*\\)");
    private static final Pattern CALL = Pattern.compile("^\\s*rename\\(", Pattern.MULTILINE);

    private record Rule(String since, String from, String to) {
    }

    private static List<Rule> rules() throws IOException {
        String script = Files.readString(Sources.project().resolve("stonecutter.gradle.kts"));
        List<Rule> rules = new ArrayList<>();
        Matcher rule = RULE.matcher(script);
        while (rule.find()) {
            rules.add(new Rule(rule.group(1), unescaped(rule.group(2)), unescaped(rule.group(3))));
        }
        // A rule written in a form the pattern misses would otherwise go unchecked, and the test would still pass.
        assertEquals(CALL.matcher(script).results().count(), rules.size(), "rules this test could not read");
        return rules;
    }

    private static String unescaped(String kotlin) {
        return kotlin.replaceAll("\\\\(.)", "$1");
    }

    private static String sources(List<Path> trees) throws IOException {
        StringBuilder all = new StringBuilder();
        for (Path tree : trees) {
            for (Path file : Sources.javaFiles(tree)) {
                all.append(Files.readString(file));
            }
        }
        return all.toString();
    }

    @Test
    void everyRuleHasAUse() throws IOException {
        String all = sources(Sources.trees());
        assertEquals(List.of(), rules().stream().filter(rule -> !all.contains(rule.from())).toList());
    }

    /** Switching the active version runs the rules backwards, which would rename that text too. */
    @Test
    void noRulesNewTextIsInCodeTheVersionsShare() throws IOException {
        Path src = Sources.project().resolve("src");
        String shared = sources(List.of(src.resolve("main"), src.resolve("1.21.1")));
        assertEquals(List.of(), rules().stream().filter(rule -> shared.contains(rule.to())).toList());
    }
}
