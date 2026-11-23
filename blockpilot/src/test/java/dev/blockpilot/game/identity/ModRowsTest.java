package dev.blockpilot.game.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.blockpilot.api.Row;
import dev.blockpilot.platform.ClientPlatform.LoadedMod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModRowsTest {
    @TempDir
    Path game;

    private Path jar(String name, String content) throws IOException {
        Path jar = game.resolve("mods").resolve(name);
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, content);
        return jar;
    }

    @Test
    void aJarIsNamedInTheGameDirectoryWithItsHash() throws IOException {
        Path jar = jar("mymod-0.3.0.jar", "abc");
        ModHashes hashes = new ModHashes();
        hashes.hash(jar);
        List<Row> rows = ModRows.rows(List.of(new LoadedMod("mymod", "0.3.0", List.of(jar), false, null)), id -> true,
            game, hashes);
        assertEquals("mymod 0.3.0 file=mods/mymod-0.3.0.jar "
            + "sha256=ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", rows.get(0).text());
    }

    @Test
    void aJarReplacedSinceItWasHashedIsChanged() throws IOException {
        Path jar = jar("mymod-0.3.0.jar", "abc");
        ModHashes hashes = new ModHashes();
        hashes.hash(jar);
        assertFalse(hashes.changed(jar));
        Files.writeString(jar, "a newer build");
        Files.setLastModifiedTime(jar, FileTime.from(Instant.now().plusSeconds(5)));
        assertTrue(hashes.changed(jar));
        Row row = ModRows.rows(List.of(new LoadedMod("mymod", "0.3.0", List.of(jar), false, null)), id -> true, game,
            hashes).get(0);
        assertTrue(row.text().endsWith(" changed"), row.text());
        assertTrue(row.text().contains("sha256=ba7816bf"), "the hash stays that of the jar as loaded: " + row.text());
    }

    @Test
    void nestedFoldersNoFileAndFilesOutsideTheGameDirectory() throws IOException {
        Path outside = Files.createTempFile("library", ".jar");
        try {
            List<LoadedMod> mods = List.of(
                new LoadedMod("zlib", "1.0", List.of(), true, "mymod"),
                new LoadedMod("mymod", "dev", List.of(game.resolve("build/classes"), game.resolve("build/res")), false,
                    null),
                new LoadedMod("library", "2.0", List.of(outside), false, null),
                new LoadedMod("minecraft", "1.21.1", List.of(), false, null));
            List<String> rows = ModRows.rows(mods, id -> true, game, null).stream().map(Row::text).toList();
            String absolute = outside.toAbsolutePath().normalize().toString().replace('\\', '/');
            assertEquals(List.of("library 2.0 file=" + absolute, "minecraft 1.21.1", "mymod dev folder=build/classes",
                "zlib 1.0 nested in=mymod"), rows);
        } finally {
            Files.delete(outside);
        }
    }

    @Test
    void aHashStillToTakeIsLeftOut() throws IOException {
        Path jar = jar("mymod.jar", "abc");
        Row row = ModRows.rows(List.of(new LoadedMod("mymod", "1", List.of(jar), false, null)), id -> true, game,
            new ModHashes()).get(0);
        assertEquals("mymod 1 file=mods/mymod.jar", row.text());
    }

    @Test
    void theFilterKeepsMatchingIds() {
        List<LoadedMod> mods = List.of(new LoadedMod("a", "1", List.of(), false, null),
            new LoadedMod("b", "1", List.of(), false, null));
        assertEquals(List.of("b"), ModRows.rows(mods, "b"::equals, game, null).stream().map(Row::key).toList());
    }
}
