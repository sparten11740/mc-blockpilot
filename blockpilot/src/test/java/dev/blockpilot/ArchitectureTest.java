package dev.blockpilot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * What each layer may depend on. The api and protocol layers stay free of Minecraft so they work on every target and
 * outside the game; the rest keeps what varies between versions and loaders behind one class each.
 */
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
    void protocolUsesNeitherMinecraftNorTheClient() throws IOException {
        assertEquals(List.of(), violations("protocol", List.of("net.minecraft", "net.neoforged", "net.fabricmc",
            "dev.blockpilot.game", "dev.blockpilot.platform", "dev.blockpilot.cli")));
    }

    /** The client runs on a bare JVM next to the jar, where neither the game's libraries nor the loader exist. */
    @Test
    void cliAndWireUseOnlyTheJdk() throws IOException {
        assertEquals(List.of(), jdkOnly("cli", "java.", "dev.blockpilot.cli.", "dev.blockpilot.wire."));
        assertEquals(List.of(), jdkOnly("wire", "java.", "dev.blockpilot.wire."));
    }

    private static List<String> jdkOnly(String layer, String... allowed) throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sources().resolve(layer))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(file)) {
                    String trimmed = line.strip();
                    String imported = trimmed.replaceFirst("^import (static )?", "");
                    boolean foreignImport = !imported.equals(trimmed)
                        && Stream.of(allowed).noneMatch(imported::startsWith);
                    if (foreignImport || trimmed.matches(".*\\b(net\\.minecraft|net\\.neoforged|net\\.fabricmc"
                        + "|com\\.google|io\\.netty|org\\.slf4j|dev\\.blockpilot\\.(api|protocol|game|platform))\\b.*")) {
                        found.add(file.getFileName() + ": " + trimmed);
                    }
                }
            }
        }
        return found;
    }

    /** A file copied between trees is a fix that lands in only one of them; what they share belongs in src/main. */
    @Test
    void noFileIsTheSameInTwoTrees() throws IOException {
        Set<Map.Entry<Path, ByteBuffer>> seen = new HashSet<>();
        List<String> same = new ArrayList<>();
        for (Path tree : Sources.codeTrees()) {
            try (Stream<Path> files = Files.walk(tree)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    Path path = tree.relativize(file);
                    if (!seen.add(Map.entry(path, ByteBuffer.wrap(Files.readAllBytes(file))))) {
                        same.add(path.toString());
                    }
                }
            }
        }
        assertEquals(List.of(), same);
    }

    /** The window system changed in 26.3. Mixins have to name it, as their signatures must match their targets. */
    @Test
    void onlyWindowSystemAndMixinsImportTheWindowSystem() throws IOException {
        assertEquals(List.of(), lines(
            file -> !file.endsWith("WindowSystem.java") && !file.getParent().endsWith("mixin"),
            line -> line.contains("import org.lwjgl.glfw") || line.contains("import org.lwjgl.sdl")));
    }

    /** What a loader's code needs from the mod goes through the platform's hooks. */
    @Test
    void loaderPackagesImportNoGameCode() throws IOException {
        for (String loader : List.of("platform/fabric", "platform/neoforge")) {
            assertEquals(List.of(), violations(loader, List.of("dev.blockpilot.game")));
        }
    }

    @Test
    void loaderConditionsStayInThePlatform() throws IOException {
        Pattern condition = Pattern.compile("//\\?.*\\b(neoforge|fabric|vanilla)\\b");
        assertEquals(List.of(), lines(file -> !file.startsWith("java/dev/blockpilot/platform"),
            line -> condition.matcher(line).find()));
    }

    /** game's commands use its subpackages, and those game.common, so each can be read without what's above it. */
    @Test
    void gamePackagesDependOneWay() throws IOException {
        Pattern commandClass = Pattern.compile("import (static )?dev\\.blockpilot\\.game\\.[A-Z]");
        assertEquals(List.of(), lines(file -> file.startsWith("java/dev/blockpilot/game") && file.getNameCount() > 5,
            line -> commandClass.matcher(line).find()));
        assertEquals(List.of(), lines(file -> file.startsWith("java/dev/blockpilot/game/common"),
            line -> line.contains("import dev.blockpilot.game.") && !line.contains("dev.blockpilot.game.common.")));
    }

    /** The lines the test picks, from the files it picks by their path in their tree, in the trees of the jars. */
    private static List<String> lines(Predicate<Path> files, Predicate<String> picked) throws IOException {
        List<String> found = new ArrayList<>();
        for (Path tree : Sources.codeTrees()) {
            for (Path file : Sources.javaFiles(tree)) {
                if (files.test(tree.relativize(file))) {
                    Files.readAllLines(file).stream().filter(picked)
                        .forEach(line -> found.add(file.getFileName() + ": " + line.strip()));
                }
            }
        }
        return found;
    }
}
