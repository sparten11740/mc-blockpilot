package dev.blockpilot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Only the Sable compat package may name Sable's classes, so without Sable none of them is ever loaded. */
class SableIsolationTest {
    @Test
    void onlyTheCompatPackageNamesSable() throws IOException {
        Path root = Paths.get("").toAbsolutePath();
        while (!Files.exists(root.resolve("settings.gradle.kts"))) {
            root = root.getParent();
        }
        Path sources = root.resolve("blockpilot/src");
        List<String> offenders;
        try (Stream<Path> files = Files.walk(sources)) {
            offenders = files.filter(file -> file.toString().endsWith(".java"))
                .filter(file -> !file.toString().contains("/dev/blockpilot/compat/sable/"))
                .filter(file -> !file.toString().contains("/src/test/"))
                .filter(file -> read(file).contains("dev.ryanhcode"))
                .map(file -> sources.relativize(file).toString())
                .toList();
        }
        assertEquals(List.of(), offenders);
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
