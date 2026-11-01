package dev.blockpilot.game.record;

import dev.blockpilot.game.record.FfmpegLookup.Platform;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The ffmpeg command lines of a recording: one encoding the raw frames into a Matroska video while it runs, one muxing
 * that video with the WAV into the MP4 at the end.
 */
final class FfmpegCommands {
    static final String X264 = "libx264";
    /** For the hardware encoders, which take a bit rate rather than a quality: bits per pixel and frame. */
    private static final double BITS_PER_PIXEL = 0.12;

    private FfmpegCommands() {
    }

    /** The H.264 encoder to use, from the output of {@code ffmpeg -encoders}: libx264, else the platform's own. */
    static Optional<String> encoder(String encoders, Platform platform) {
        List<String> video = new ArrayList<>();
        for (String line : encoders.lines().toList()) {
            String[] words = line.trim().split("\\s+");
            if (words.length >= 2 && words[0].length() == 6 && words[0].charAt(0) == 'V') {
                video.add(words[1]);
            }
        }
        String hardware = switch (platform) {
            case MAC -> "h264_videotoolbox";
            case WINDOWS -> "h264_mf";
            case LINUX -> null;
        };
        return video.contains(X264) ? Optional.of(X264)
            : hardware != null && video.contains(hardware) ? Optional.of(hardware) : Optional.empty();
    }

    /** Reads RGBA frames with their rows bottom up, as the framebuffer holds them, from standard input. */
    static List<String> encode(String ffmpeg, String encoder, int width, int height, int fps, Path video) {
        List<String> command = new ArrayList<>(List.of(ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
            "-f", "rawvideo", "-pix_fmt", "rgba", "-s", width + "x" + height, "-framerate", String.valueOf(fps),
            "-i", "-", "-vf", "vflip", "-pix_fmt", "yuv420p", "-c:v", encoder));
        if (encoder.equals(X264)) {
            command.addAll(List.of("-preset", "veryfast", "-crf", "18"));
        } else {
            command.addAll(List.of("-b:v", String.valueOf(Math.round(BITS_PER_PIXEL * width * height * fps))));
        }
        command.addAll(List.of("-f", "matroska", video.toString()));
        return command;
    }

    /** {@code audio} is null for a silent recording. */
    static List<String> mux(String ffmpeg, Path video, Path audio, Path output) {
        List<String> command = new ArrayList<>(List.of(ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
            "-i", video.toString()));
        if (audio != null) {
            command.addAll(List.of("-i", audio.toString(), "-map", "0:v", "-map", "1:a", "-c:v", "copy",
                "-c:a", "aac", "-b:a", "192k", "-ar", "48000", "-ac", "2"));
        } else {
            command.addAll(List.of("-map", "0:v", "-c:v", "copy"));
        }
        command.addAll(List.of("-movflags", "+faststart", output.toString()));
        return command;
    }
}
