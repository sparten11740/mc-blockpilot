package dev.blockpilot.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The script the game writes into {@code <gameDir>/blockpilot} at every start: it runs this client on the game's own
 * Java with the jar (or dev folders) the game loaded, so a shell needs no {@code java} on its path. The game
 * directory and the version come preset; a caller's {@code --game-dir} comes later and wins.
 */
public final class Launcher {
    static final String MAIN_CLASS = "dev.blockpilot.cli.Main";
    static final String VERSION_PROPERTY = "blockpilot.version";
    private static final String GONE = " is gone; start the game once to update this launcher";

    private Launcher() {
    }

    public static boolean onWindows() {
        return System.getProperty("os.name", "").startsWith("Windows");
    }

    public static String fileName(boolean windows) {
        return windows ? "blockpilot.cmd" : "blockpilot";
    }

    /** The Java the game runs on, as a console program ({@code java.exe}, not the {@code javaw.exe} it may run as). */
    public static Path java(boolean windows) {
        return Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java");
    }

    public static String text(boolean windows, Path java, List<Path> classpath, Path gameDir, String version) {
        List<Path> needed = new ArrayList<>(classpath);
        needed.add(0, java);
        String joined = classpath.stream().map(Path::toString).collect(Collectors.joining(windows ? ";" : ":"));
        List<String> command = List.of(java.toString(), "-D" + VERSION_PROPERTY + "=" + version, "-cp", joined,
            MAIN_CLASS, "--game-dir", gameDir.toString());
        return windows ? cmd(needed, command) : sh(needed, command);
    }

    private static String sh(List<Path> needed, List<String> command) {
        StringBuilder script = new StringBuilder("#!/bin/sh\n")
            .append("# Written by Blockpilot at every game start: runs its client on the game's own Java.\n")
            .append("for needed in");
        needed.forEach(path -> script.append(' ').append(shQuote(path.toString())));
        script.append("; do\n")
            .append("    if [ ! -e \"$needed\" ]; then\n")
            .append("        echo \"blockpilot: $needed").append(GONE).append("\" >&2\n")
            .append("        exit 2\n")
            .append("    fi\n")
            .append("done\n")
            .append("exec");
        command.forEach(word -> script.append(' ').append(shQuote(word)));
        return script.append(" \"$@\"\n").toString();
    }

    private static String cmd(List<Path> needed, List<String> command) {
        StringBuilder script = new StringBuilder("@echo off\r\n")
            .append("rem Written by Blockpilot at every game start: runs its client on the game's own Java.\r\n")
            .append("for %%n in (");
        script.append(needed.stream().map(path -> cmdQuote(path.toString())).collect(Collectors.joining(" ")));
        script.append(") do if not exist %%n (\r\n")
            .append("    echo blockpilot: %%~n").append(GONE).append(" 1>&2\r\n")
            .append("    exit /b 2\r\n")
            .append(")\r\n");
        script.append(command.stream().map(Launcher::cmdQuote).collect(Collectors.joining(" ")));
        // cmd reads a script line by line from an offset, and the game may rewrite this file while Java runs; exit
        // on the same line so nothing after it is read, keeping Java's exit code.
        return script.append(" %* & exit /b\r\n").toString();
    }

    private static String shQuote(String word) {
        return "'" + word.replace("'", "'\\''") + "'";
    }

    /** Windows paths can't hold {@code "}; inside quotes only {@code %} still needs escaping in a script. */
    private static String cmdQuote(String word) {
        return "\"" + word.replace("%", "%%") + "\"";
    }
}
