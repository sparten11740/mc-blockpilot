package dev.blockpilot.game.record;

import dev.blockpilot.api.CommandException;
import dev.blockpilot.game.record.FfmpegLookup.Found;
import dev.blockpilot.game.record.FfmpegLookup.Platform;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * The ffmpeg recordings run and its H.264 encoder. Found once per game session; a failed lookup is not kept, so
 * installing ffmpeg needs no restart.
 */
final class Ffmpeg {
    record Tool(String path, String encoder) {
    }

    private static final long PROBE_SECONDS = 10;
    private static String foundFor;
    private static Tool found;

    private Ffmpeg() {
    }

    static synchronized Tool find(String configured) throws CommandException {
        if (found != null && Objects.equals(foundFor, configured)) {
            return found;
        }
        Platform platform = Platform.current();
        Found lookup = FfmpegLookup.find(configured, platform, System.getenv(),
            path -> Files.isRegularFile(Path.of(path)));
        if (lookup.ffmpeg() == null) {
            throw new CommandException("unsupported", "record needs ffmpeg; none in " + String.join(", ",
                lookup.searched()) + ". Install it, or set \"ffmpeg\" in config/blockpilot/config.json to its path");
        }
        String encoder = FfmpegCommands.encoder(encoders(lookup.ffmpeg()), platform)
            .orElseThrow(() -> new CommandException("unsupported", lookup.ffmpeg() + " has no H.264 encoder ("
                + FfmpegCommands.X264 + ", or the platform's own); install an ffmpeg with libx264"));
        foundFor = configured;
        found = new Tool(lookup.ffmpeg(), encoder);
        return found;
    }

    /** Into a file rather than a pipe, so the wait can time out without reading alongside. */
    private static String encoders(String ffmpeg) throws CommandException {
        Path output = null;
        try {
            output = Files.createTempFile("blockpilot-ffmpeg-encoders", ".txt");
            Process process = new ProcessBuilder(ffmpeg, "-hide_banner", "-encoders").redirectErrorStream(true)
                .redirectOutput(output.toFile()).start();
            if (!process.waitFor(PROBE_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new CommandException("unsupported", ffmpeg + " -encoders did not answer");
            }
            return Files.readString(output, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CommandException("unsupported", "could not run " + ffmpeg + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CommandException("unsupported", "interrupted while asking " + ffmpeg + " for its encoders");
        } finally {
            if (output != null) {
                output.toFile().delete();
            }
        }
    }
}
