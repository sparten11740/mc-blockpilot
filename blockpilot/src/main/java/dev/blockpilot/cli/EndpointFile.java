package dev.blockpilot.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** What {@code <gameDir>/blockpilot/endpoint.json} says about the running game, with the token it names. */
record EndpointFile(long pid, int port, String token) {
    static EndpointFile read(Path gameDir) throws IOException {
        Path file = gameDir.resolve("blockpilot").resolve("endpoint.json");
        Map<?, ?> json;
        try {
            json = Json.parse(Files.readString(file, StandardCharsets.UTF_8)) instanceof Map<?, ?> object ? object
                : Map.of();
        } catch (NoSuchFileException e) {
            throw new IOException("no " + file + " (the game is not up)");
        } catch (IllegalArgumentException e) {
            throw new IOException(file + ": " + e.getMessage());
        }
        long pid = number(json, "pid").longValue();
        // A file left behind by a game that has exited names a port another game may hold by now.
        if (!alive(pid)) {
            throw new IOException("endpoint.json is stale (game process " + pid + " is not running)");
        }
        int port = number(json, "port").intValue();
        if (!(json.get("token_file") instanceof String tokenFile)) {
            throw new IOException("endpoint.json names no token_file");
        }
        return new EndpointFile(pid, port, Files.readString(Path.of(tokenFile), StandardCharsets.UTF_8).strip());
    }

    private static BigDecimal number(Map<?, ?> json, String key) throws IOException {
        if (json.get(key) instanceof BigDecimal number) {
            return number;
        }
        throw new IOException("endpoint.json names no " + key);
    }

    static boolean alive(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    /** True once the process has exited, false if it still runs after the given seconds. */
    static boolean waitExit(long pid, double seconds) {
        Optional<ProcessHandle> process = ProcessHandle.of(pid);
        if (process.isEmpty()) {
            return true;
        }
        try {
            process.get().onExit().get((long) (seconds * 1000), TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return !process.get().isAlive();
        } catch (ExecutionException e) {
            return !process.get().isAlive();
        }
    }
}
