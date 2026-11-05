package dev.blockpilot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The api and protocol layers must stay free of Minecraft so they work on every target and outside the game. */
class ArchitectureTest {
    private static Path sources() {
        return Sources.project().resolve("src/main/java/dev/blockpilot");
    }

    private static List<String> violations(String layer, List<String> forbidden) throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sources().resolve(layer))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(file)) {
                    String trimmed = line.strip();
                    if (trimmed.startsWith("import ") && forbidden.stream().anyMatch(trimmed::contains)) {
                        found.add(file.getFileName() + ": " + trimmed);
                    }
                }
            }
        }
        return found;
    }

    @Test
    void apiUsesNeitherMinecraftNorBlockpilotInternals() throws IOException {
        assertEquals(List.of(), violations("api", List.of("net.minecraft", "net.neoforged", "net.fabricmc",
            "dev.blockpilot.protocol", "dev.blockpilot.game", "dev.blockpilot.platform", "com.google.gson")));
    }

    @Test
    void protocolUsesNoMinecraft() throws IOException {
        assertEquals(List.of(), violations("protocol", List.of("net.minecraft", "net.neoforged", "net.fabricmc",
            "dev.blockpilot.game", "dev.blockpilot.platform")));
    }

    /** The client runs on a bare JVM next to the jar, where neither the game's libraries nor the loader exist. */
    @Test
    void cliUsesOnlyTheJdk() throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sources().resolve("cli"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(file)) {
                    String trimmed = line.strip();
                    String imported = trimmed.replaceFirst("^import (static )?", "");
                    boolean foreignImport = !imported.equals(trimmed) && !imported.startsWith("java.")
                        && !imported.startsWith("dev.blockpilot.cli.");
                    if (foreignImport || trimmed.matches(".*\\b(net\\.minecraft|net\\.neoforged|net\\.fabricmc"
                        + "|com\\.google|io\\.netty|org\\.slf4j|dev\\.blockpilot\\.(api|protocol|game|platform))\\b.*")) {
                        found.add(file.getFileName() + ": " + trimmed);
                    }
                }
            }
        }
        assertEquals(List.of(), found);
    }

    /** A file copied between trees is a fix that lands in only one of them; what they share belongs in src/main. */
    @Test
    void noFileIsTheSameInTwoTrees() throws IOException {
        Map<Path, Path> seen = new HashMap<>();
        List<String> same = new ArrayList<>();
        for (Path tree : Sources.trees()) {
            if (tree.endsWith("test")) {
                continue;
            }
            try (Stream<Path> files = Files.walk(tree)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    Path other = seen.putIfAbsent(tree.relativize(file), file);
                    if (other != null && Files.mismatch(other, file) == -1) {
                        same.add(tree.relativize(file).toString());
                    }
                }
            }
        }
        assertEquals(List.of(), same);
    }
}
