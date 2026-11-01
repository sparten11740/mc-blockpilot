package dev.blockpilot.game.record;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.blockpilot.game.record.FfmpegLookup.Platform;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FfmpegCommandsTest {
    private static final String HOMEBREW = """
        Encoders:
         V..... = Video
         A..... = Audio
         ------
         V....D libx264              libx264 H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10 (codec h264)
         V....D h264_videotoolbox    VideoToolbox H.264 Encoder (codec h264)
         A....D aac                  AAC (Advanced Audio Coding)
        """;
    private static final String WITHOUT_X264 = """
         V....D h264_videotoolbox    VideoToolbox H.264 Encoder (codec h264)
         V....D h264_mf              H264 via MediaFoundation (codec h264)
         A....D aac                  AAC (Advanced Audio Coding)
        """;

    @Test
    void libx264ComesFirst() {
        assertEquals(Optional.of("libx264"), FfmpegCommands.encoder(HOMEBREW, Platform.MAC));
        assertEquals(Optional.of("libx264"), FfmpegCommands.encoder(HOMEBREW, Platform.LINUX));
    }

    @Test
    void withoutLibx264ThePlatformsOwnEncoderIsTaken() {
        assertEquals(Optional.of("h264_videotoolbox"), FfmpegCommands.encoder(WITHOUT_X264, Platform.MAC));
        assertEquals(Optional.of("h264_mf"), FfmpegCommands.encoder(WITHOUT_X264, Platform.WINDOWS));
        assertEquals(Optional.empty(), FfmpegCommands.encoder(WITHOUT_X264, Platform.LINUX));
    }

    @Test
    void theLegendIsNotAnEncoder() {
        assertEquals(Optional.empty(), FfmpegCommands.encoder(" V..... = Video\n A..... = Audio\n", Platform.MAC));
    }

    @Test
    void encodesFlippedRgbaFramesWithLibx264() {
        assertEquals(List.of("ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "rawvideo", "-pix_fmt",
            "rgba", "-s", "1920x1080", "-framerate", "60", "-i", "-", "-vf", "vflip", "-pix_fmt", "yuv420p", "-c:v",
            "libx264", "-preset", "veryfast", "-crf", "18", "-f", "matroska", "/r/.take.video.mkv"),
            FfmpegCommands.encode("ffmpeg", "libx264", 1920, 1080, 60, Path.of("/r/.take.video.mkv")));
    }

    @Test
    void hardwareEncodersGetABitRateForTheSizeAndFrameRate() {
        List<String> command = FfmpegCommands.encode("ffmpeg", "h264_videotoolbox", 1280, 720, 30,
            Path.of("/r/.take.video.mkv"));
        assertEquals(List.of("-c:v", "h264_videotoolbox", "-b:v", "3317760", "-f", "matroska", "/r/.take.video.mkv"),
            command.subList(command.size() - 7, command.size()));
    }

    @Test
    void muxesTheVideoWithTheWavAsAac() {
        assertEquals(List.of("/bin/ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", "/r/.take.video.mkv",
            "-i", "/r/.take.wav", "-map", "0:v", "-map", "1:a", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-ar",
            "48000", "-ac", "2", "-movflags", "+faststart", "/r/take.mp4"),
            FfmpegCommands.mux("/bin/ffmpeg", Path.of("/r/.take.video.mkv"), Path.of("/r/.take.wav"),
                Path.of("/r/take.mp4")));
    }

    @Test
    void aSilentRecordingIsMuxedWithoutAudio() {
        assertEquals(List.of("ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", "/r/.clip.video.mkv",
            "-map", "0:v", "-c:v", "copy", "-movflags", "+faststart", "/r/clip.mp4"),
            FfmpegCommands.mux("ffmpeg", Path.of("/r/.clip.video.mkv"), null, Path.of("/r/clip.mp4")));
    }
}
