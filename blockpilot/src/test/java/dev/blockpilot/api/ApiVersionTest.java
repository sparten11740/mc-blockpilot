package dev.blockpilot.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/** The published blockpilot-api artifact's version (gradle.properties) must be the one the API reports. */
class ApiVersionTest {
    @Test
    void publishedVersionMatchesTheCode() throws IOException {
        Path directory = Paths.get("").toAbsolutePath();
        while (directory != null && !Files.exists(directory.resolve("settings.gradle.kts"))) {
            directory = directory.getParent();
        }
        assertTrue(directory != null, "could not find the repository root");
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(directory.resolve("gradle.properties"))) {
            properties.load(reader);
        }
        assertEquals(BlockpilotApi.VERSION, properties.getProperty("api_version"));
    }
}
