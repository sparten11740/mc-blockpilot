package dev.blockpilot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The api and protocol layers must stay free of Minecraft so they work on every target and outside the game. */
class ArchitectureTest {
    private static Path sources() {
        Path directory = Paths.get("").toAbsolutePath();
        while (directory != null && !Files.isDirectory(directory.resolve("src/main/java/dev/blockpilot/api"))) {
            directory = directory.getParent();
        }
        assertTrue(directory != null, "could not find the source tree above " + Paths.get("").toAbsolutePath());
        return directory.resolve("src/main/java/dev/blockpilot");
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
}
