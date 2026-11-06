package dev.blockpilot.game.record;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Duration;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Numbers;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import dev.blockpilot.game.common.FrameCapture;
import dev.blockpilot.game.common.TimedStop;
import dev.blockpilot.platform.ClientPlatform;
import dev.blockpilot.protocol.CommandRegistry;
import dev.blockpilot.protocol.Engine;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** {@code record start|stop|status} and {@code record <duration>}: videos of the game under blockpilot/recordings/. */
public final class RecordCommands {
    private static final int DEFAULT_MAX_WIDTH = 1920;
    private static final int MIN_WIDTH = 16;
    private static final int DEFAULT_FPS = 60;
    private static final int MAX_FPS = 120;

    private final Supplier<String> ffmpeg;

    /** {@code ffmpeg} gives the configured ffmpeg path, empty to look for one. */
    public RecordCommands(Supplier<String> ffmpeg) {
        this.ffmpeg = ffmpeg;
    }

    public void register(CommandRegistry registry) {
        registry.register(CommandSpec.action("record", this::record).group("observe")
            .form(form -> form.keyword("op", "start").optionalArg("name", ArgType.WORD))
            .form(form -> form.keyword("op", "stop", "status"))
            .form(form -> form.keyword("op", "mark").arg("label", ArgType.WORD))
            .form(form -> form.arg("for", ArgType.DURATION).optionalArg("name", ArgType.WORD))
            .option("width", "the video's width (default the frame's, up to 1920)", ArgType.INT)
            .option("fps", "frames per second, 1..120 (default 60)", ArgType.INT)
            .option("hud", "off: hide the GUI, crosshair and hand while recording", List.of("on", "off"))
            .option("audio", "off: record without sound", List.of("on", "off"))
            .help("record start [name] … record stop: records the game to blockpilot/recordings/<name>.mp4 (H.264, "
                + "with AAC sound unless audio=off), from its framebuffer, so the window can be in the background. "
                + "Names use letters, digits, _ . - (default recording-<date>-<time>); a taken name fails with err "
                + "exists. width= is the video's width (default the frame's, up to 1920, never wider; the height "
                + "follows the frame's shape), fps= 1..120 (default 60), hud=off hides the GUI while recording. A "
                + "frame the game had not rendered anew by its time repeats the one before. The sound is the game's "
                + "mix at its volumes (option soundCategory_music 0 leaves the music out), heard from the camera, in "
                + "plain stereo, and still plays through the speakers; starting and stopping with sound reopens the "
                + "sound device, so one-shot sounds and music playing then end while loops carry on. record stop "
                + "answers with the file, its seconds, size and fps, the frames, how many repeat, whether it has "
                + "sound, and the encoder. record status: whether one runs. record mark <label>: notes the video time "
                + "the script reached it at (0 before the first frame, as record status counts it) and answers with "
                + "it and frame=<k>, the first video frame from it, without costing the script a tick; a change the "
                + "client makes right after it shows from frame k or k+1, a server command's change a tick or two "
                + "later. record stop lists the marks as rows, and each mark rewrites <name>.marks.json beside the "
                + "video ({\"video\", \"marks\": [{\"label\", \"seconds\", \"frame\"}]}), from which a script can "
                + "write a video description's 0:00 chapters; record start refuses a name whose marks file exists, a "
                + "failed take takes its marks file with it, and a recording takes at most 1000 marks. record "
                + "<duration> [name]: records that long and answers as record stop does, so its take has no marks; "
                + "stop cancels the wait and leaves the recording running. Needs "
                + "ffmpeg, on the PATH, in the usual install folders, or set as \"ffmpeg\" in "
                + "config/blockpilot/config.json")
            .field("recording", "the recording's name").field("file", "the saved video")
            .field("seconds", "how long the video lasts").field("width", "the video's width")
            .field("height", "the video's height").field("fps", "frames per second")
            .field("frames", "the video's frames").field("repeated", "frames repeating the one before")
            .field("audio", "on or off").field("encoder", "the H.264 encoder ffmpeg used")
            .field("mark", "record mark: the label").field("frame", "record mark: the first video frame from it")
            .example("record 10s showcase hud=off"));
    }

    private Action record(CommandContext context, Args args) throws CommandException {
        if (args.has("for")) {
            start(args);
            return new Timed(args.duration("for"));
        }
        switch (args.word("op")) {
            case "start" -> {
                Recording started = start(args);
                Fields fields = new Fields().put("recording", started.name(), "recording " + started.name());
                return () -> Result.ok(size(fields, started).put("audio", started.audio() ? "on" : "off"));
            }
            case "mark" -> {
                return mark(args.word("label"));
            }
            case "status" -> {
                if (Recorder.failure() != null) {
                    throw new CommandException("record_failed", Recorder.failure());
                }
                Recording running = Recorder.recording();
                if (running == null) {
                    return () -> Result.ok(new Fields().put("recording", "none", "no recording running"));
                }
                double seconds = running.seconds();
                Fields fields = new Fields().bare("recording", running.name())
                    .put("seconds", seconds, Numbers.format(seconds) + "s")
                    .put("frames", running.framesReadBack());
                if (running.stopping()) {
                    fields.put("finishing", true, "finishing");
                }
                return () -> Result.ok(fields);
            }
            default -> {
                if (Recorder.failure() != null) {
                    throw new CommandException("record_failed", Recorder.takeFailure());
                }
                if (Recorder.recording() == null || Recorder.recording().stopping()) {
                    throw new CommandException("no_capture", "no recording is running");
                }
                return new Stop(Recorder.recording());
            }
        }
    }

    private static Action mark(String label) throws CommandException {
        if (Recorder.failure() != null) {
            throw new CommandException("record_failed", Recorder.failure());
        }
        Recording running = Recorder.recording();
        if (running == null || running.stopping()) {
            throw new CommandException("no_capture", "no recording is running");
        }
        if (running.marks() >= Recording.MAX_MARKS) {
            throw new CommandException("too_large", "a recording takes at most " + Recording.MAX_MARKS + " marks");
        }
        return () -> Result.ok(markFields(new Fields().put("mark", label, "mark " + label), running.mark(label)));
    }

    private static Fields markFields(Fields fields, Recording.Mark mark) {
        return fields.put("seconds", mark.seconds(), Numbers.format(mark.seconds()) + "s")
            .put("frame", mark.frame(), "frame=" + mark.frame());
    }

    private Recording start(Args args) throws CommandException {
        Recording running = Recorder.recording();
        if (running != null) {
            throw new CommandException("busy", "recording " + running.name() + " is "
                + (running.stopping() ? "finishing" : "running; record stop first"));
        }
        String name = args.has("name") ? args.word("name")
            : "recording-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        if (!name.matches("[A-Za-z0-9_.-]{1,64}") || name.startsWith(".")) {
            throw new CommandException("syntax", "recording names use letters, digits, _ . - (max 64), not "
                + "starting with .");
        }
        RenderTarget frame = FrameCapture.mainTarget();
        int asked = args.intOption("width", Math.min(frame.width, DEFAULT_MAX_WIDTH));
        if (asked < MIN_WIDTH) {
            throw new CommandException("syntax", "width is at least " + MIN_WIDTH);
        }
        int fps = args.intOption("fps", DEFAULT_FPS);
        if (fps < 1 || fps > MAX_FPS) {
            throw new CommandException("syntax", "fps takes 1.." + MAX_FPS);
        }
        boolean audio = !"off".equals(args.option("audio"));
        if (audio && AudioCapture.unavailable() != null) {
            throw new CommandException("unsupported", AudioCapture.unavailable() + "; audio=off records without sound");
        }
        // H.264 in 4:2:0 needs even sides.
        int width = Math.min(asked, frame.width) & ~1;
        int height = Math.max(2, (int) ((long) frame.height * width / frame.width) & ~1);
        Path directory = ClientPlatform.gameDir().resolve("blockpilot").resolve("recordings");
        Path file = directory.resolve(name + ".mp4");
        if (Files.exists(file)) {
            throw new CommandException("exists", file + " exists; choose another name");
        }
        Path marks = Recording.marksFile(directory, name);
        if (Files.exists(marks)) {
            throw new CommandException("exists", marks + " exists, the marks of another take; choose another name");
        }
        Ffmpeg.Tool tool = Ffmpeg.find(ffmpeg.get());
        if (audio) {
            String failed = AudioCapture.use(true);
            if (failed != null) {
                throw new CommandException("unsupported", failed + "; audio=off records without sound");
            }
        }
        try {
            Files.createDirectories(directory);
            Recording started = new Recording(name, directory, width, height, fps, tool,
                "off".equals(args.option("hud")), audio);
            Recorder.started(started);
            return started;
        } catch (IOException | RuntimeException e) {
            if (audio) {
                AudioCapture.use(false);
            }
            throw new CommandException("record_failed", "could not start ffmpeg: " + e.getMessage());
        }
    }

    private static Fields size(Fields fields, Recording recording) {
        return fields.put("width", recording.width(), recording.width() + "x" + recording.height())
            .hidden("height", recording.height()).put("fps", recording.fps(), recording.fps() + "fps");
    }

    /** Ends the recording once it has a frame, then answers when the file is saved. */
    private static final class Stop implements Action {
        private final Recording recording;
        private boolean stopped;

        Stop(Recording recording) {
            this.recording = recording;
        }

        @Override
        public Result tick() throws CommandException {
            if (!stopped) {
                if (Recorder.recording() != recording) {
                    throw new CommandException("record_failed", Recorder.takeFailure());
                }
                if (!recording.hasFrames()) {
                    return null;
                }
                recording.stop();
                stopped = true;
            }
            if (!recording.finished().isDone()) {
                return null;
            }
            Recorder.reported(recording);
            try {
                Recording.Summary summary = recording.finished().join();
                Fields fields = new Fields().bare("file", summary.file().toString())
                    .put("seconds", summary.seconds(), Numbers.format(summary.seconds()) + "s")
                    .put("width", summary.width(), summary.width() + "x" + summary.height())
                    .hidden("height", summary.height()).put("fps", summary.fps(), summary.fps() + "fps")
                    .put("frames", summary.frames()).put("repeated", summary.repeats())
                    .put("audio", summary.audio() ? "on" : "off").put("encoder", summary.encoder());
                List<Row> rows = new ArrayList<>();
                for (Recording.Mark mark : summary.marks()) {
                    rows.add(Row.of(mark.label(), markFields(new Fields().bare("label", mark.label()), mark)));
                }
                return Result.ok(fields).withRows(rows);
            } catch (RuntimeException e) {
                throw new CommandException("record_failed", Recorder.message(e));
            }
        }

        @Override
        public String label() {
            return "record";
        }
    }

    /**
     * {@code record <duration>}: waits that long on the wall clock, then stops the recording as {@code record stop}
     * does.
     */
    private static final class Timed extends TimedStop implements Engine.SelfTimed {
        Timed(Duration duration) {
            super(duration);
        }

        @Override
        protected Action stop() throws CommandException {
            if (Recorder.recording() == null) {
                throw new CommandException("record_failed", Recorder.takeFailure());
            }
            return new Stop(Recorder.recording());
        }

        @Override
        public String label() {
            return "record";
        }
    }
}
