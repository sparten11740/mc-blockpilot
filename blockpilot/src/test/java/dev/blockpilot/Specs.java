package dev.blockpilot;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** The OpenSpec files as tests read them. */
public final class Specs {
    private static final String EBNF = "```ebnf\n";

    private Specs() {
    }

    /** The grammar the code implements: that of an active change that modifies it, else the main spec's. */
    public static String grammar() throws IOException {
        Path root = Paths.get("").toAbsolutePath();
        while (!Files.exists(root.resolve("settings.gradle.kts"))) {
            root = root.getParent();
        }
        List<String> changed = new ArrayList<>();
        try (Stream<Path> changes = Files.list(root.resolve("openspec/changes"))) {
            for (Path change : changes.filter(path -> !path.getFileName().toString().equals("archive")).toList()) {
                Path delta = change.resolve("specs/command-language/spec.md");
                if (Files.exists(delta) && Files.readString(delta).contains(EBNF)) {
                    changed.add(Files.readString(delta));
                }
            }
        }
        assertTrue(changed.size() <= 1, "more than one active change modifies the grammar");
        String spec = changed.isEmpty() ? Files.readString(root.resolve("openspec/specs/command-language/spec.md"))
            : changed.get(0);
        int start = spec.indexOf(EBNF) + EBNF.length();
        return spec.substring(start, spec.indexOf("```", start));
    }
}
