package dev.blockpilot.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class LauncherTest {
    @TempDir
    Path directory;

    private Path java;
    private Path jar;
    private Path folder;
    private Path gameDir;

    @BeforeEach
    void fakeInstall() throws IOException {
        java = directory.resolve("fake java");
        Files.writeString(java, "#!/bin/sh\nfor word in \"$@\"; do printf '[%s]\\n' \"$word\"; done\n");
        assertTrue(java.toFile().setExecutable(true));
        jar = Files.createFile(directory.resolve("it's blockpilot.jar"));
        folder = Files.createDirectories(directory.resolve("dev $HOME folder"));
        gameDir = directory.resolve("game dir");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void theShellScriptHandsEveryWordToJavaIntact() throws Exception {
        String output = runLauncher(List.of(jar, folder), "status; look", "--game-dir", "other");
        assertEquals(String.join("\n", "[-Dblockpilot.version=1.2]", "[-cp]", "[" + jar + ":" + folder + "]",
            "[dev.blockpilot.cli.Main]", "[--game-dir]", "[" + gameDir + "]", "[status; look]", "[--game-dir]",
            "[other]", "exit 0"), output);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void aMissingJarStopsTheShellScript() throws Exception {
        Files.delete(jar);
        assertEquals("blockpilot: " + jar + " is gone; start the game once to update this launcher\nexit 2",
            runLauncher(List.of(jar, folder), "status"));
    }

    @Test
    void theCmdScriptQuotesForCmd() {
        List<Path> classpath = List.of(Path.of("C:\\mods\\100% & more.jar"), Path.of("C:\\dev\\classes"));
        String text = Launcher.text(true, Path.of("C:\\Java 21\\bin\\java.exe"), classpath, Path.of("C:\\games\\run"),
            "1.2");
        assertEquals("""
            @echo off\r
            rem Written by Blockpilot at every game start: runs its client on the game's own Java.\r
            for %%n in ("C:\\Java 21\\bin\\java.exe" "C:\\mods\\100%% & more.jar" "C:\\dev\\classes") do \
            if not exist %%n (\r
                echo blockpilot: %%~n is gone; start the game once to update this launcher 1>&2\r
                exit /b 2\r
            )\r
            "C:\\Java 21\\bin\\java.exe" "-Dblockpilot.version=1.2" \
            "-cp" "C:\\mods\\100%% & more.jar;C:\\dev\\classes" \
            "dev.blockpilot.cli.Main" "--game-dir" "C:\\games\\run" %* & exit /b\r
            """, text);
    }

    @Test
    void namesTheConsoleJavaOfThisRuntime() {
        assertEquals(Path.of(System.getProperty("java.home"), "bin", "java.exe"), Launcher.java(true));
        assertEquals("blockpilot.cmd", Launcher.fileName(true));
        assertEquals("blockpilot", Launcher.fileName(false));
    }

    private String runLauncher(List<Path> classpath, String... args) throws Exception {
        Path launcher = directory.resolve("blockpilot");
        Files.writeString(launcher, Launcher.text(false, java, classpath, gameDir, "1.2"));
        List<String> command = new ArrayList<>(List.of("sh", launcher.toString()));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return output + "exit " + process.waitFor();
    }
}
