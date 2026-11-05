package dev.blockpilot;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

/** The sources as checked in, for tests about the code itself; tests run from a node's directory, far below them. */
public final class Sources {
    private Sources() {
    }

    public static Path project() {
        Path directory = Paths.get("").toAbsolutePath();
        while (directory != null && !Files.isDirectory(directory.resolve("src/main/java/dev/blockpilot/api"))) {
            directory = directory.getParent();
        }
        assertTrue(directory != null, "could not find the source tree above " + Paths.get("").toAbsolutePath());
        return directory;
    }

    public static List<Path> trees() throws IOException {
        try (Stream<Path> trees = Files.list(project().resolve("src"))) {
            return trees.filter(Files::isDirectory).sorted().toList();
        }
    }

    /** The trees that go into the jars. */
    public static List<Path> codeTrees() throws IOException {
        return trees().stream().filter(tree -> !tree.endsWith("test")).toList();
    }

    public static List<Path> javaFiles(Path tree) throws IOException {
        try (Stream<Path> files = Files.walk(tree)) {
            return files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }
}
