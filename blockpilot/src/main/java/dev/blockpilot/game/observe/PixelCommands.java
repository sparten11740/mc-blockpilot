package dev.blockpilot.game.observe;

import com.mojang.blaze3d.platform.NativeImage;
import dev.blockpilot.Blockpilot;
import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Duration;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Lexicon;
import dev.blockpilot.api.Numbers;
import dev.blockpilot.api.Position;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import dev.blockpilot.game.common.FrameCapture;
import dev.blockpilot.protocol.CommandRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** {@code pixels}: colour and brightness of parts of the last rendered frame, for checks on what was drawn. */
public final class PixelCommands {
    private static final double DEFAULT_RADIUS = 0.5;
    /** The longest flicker reading, in ticks: 10 s. */
    private static final int MAX_FLICKER_TICKS = 200;
    private static volatile Projection camera;

    /** Called by the renderer with the matrices of the frame it is about to draw. */
    public static void recordFrame(Vec3 position, Matrix4f view, Matrix4f projection) {
        camera = new Projection(position.x, position.y, position.z, view.get(new float[16]),
            projection.get(new float[16]));
    }

    public void register(CommandRegistry registry) {
        registry.register(CommandSpec.action("flicker", this::flicker).group("observe").needsWorld()
            .form(form -> form.arg("duration", ArgType.DURATION).optionalText("regions"))
            .help("reads the mean brightness (luma, 0-255) of parts of every frame drawn over the duration (2t to "
                + "10s), the parts as pixels takes them, and gives each part's luma, min and max over the frames and "
                + "its strongest rhythm: flicker=<hz> in cycles per game second (20 ticks, so it holds under a slowed "
                + "tick rate), period=<ticks>t and amplitude=<luma>, or flicker=none when no rhythm of at least 1 "
                + "repeats twice over the frames, so a light switched on once has none (min and max show the change); "
                + "it resolves up to half the frame rate, fps=, in frames per game second; a row per frame gives "
                + "when it was drawn, drawn=<client tick>, and each part's luma; fewer than two frames drawn fail "
                + "with err no_frames")
            .field("frames", "frames read").field("fps", "frames per game second")
            .example("flicker 2s lamp=@~0,1,3 r=0.4"));
        registry.register(CommandSpec.query("pixels", this::pixels).group("observe")
            .form(form -> form.optionalText("regions"))
            .help("measures parts of the last rendered frame (GUI included unless hidden): [name=]x0,y0,x1,y1 in "
                + "frame fractions from the top left, or [name=]@<pos>, the square around a world position reaching "
                + "r=<blocks> to each side (default 0.5); unnamed ones are r0, r1, …, and with none all and centre. "
                + "Each gives a field <name> holding r g b luma (means), max and p90 (luma), 0-255, nested in JSON as "
                + "{\"<name>\":{\"r\":…}} and read in tests as <name>.r; err offscreen <name> when a position is "
                + "behind the camera or out of the frame. A frame rendered from a placed camera (camera at) adds "
                + "camera <x,y,z>, so no region may be named camera")
            .field("camera", "the placed camera's absolute position, when the frame was rendered from it")
            .example("pixels sky=0.4,0.05,0.6,0.15 @~0,0,4 r=1"));
    }

    private Result pixels(CommandContext context, Args args) throws CommandException {
        Map<String, Region> regions = regions(context, args.has("regions") ? args.text("regions") : "");
        Position frameCamera = CameraControl.frameCamera();
        try (NativeImage image = FrameCapture.read()) {
            PixelStats.Frame frame = frameOf(image);
            Fields fields = new Fields();
            for (Map.Entry<String, Region> region : regions.entrySet()) {
                Map<String, Object> stats = PixelStats.of(frame, region.getValue().rect()).asMap();
                fields.put(region.getKey(), stats, region.getKey() + " rgb=" + Numbers.format((double) stats.get("r"))
                    + "," + Numbers.format((double) stats.get("g")) + "," + Numbers.format((double) stats.get("b"))
                    + " luma=" + Numbers.format((double) stats.get("luma")) + " max=" + stats.get("max") + " p90="
                    + stats.get("p90"));
            }
            return Result.ok(CameraControl.withFrameCamera(fields, frameCamera));
        }
    }

    private Action flicker(CommandContext context, Args args) throws CommandException {
        int ticks = args.duration("duration").ticks();
        if (ticks < 2 || ticks > MAX_FLICKER_TICKS) {
            throw new CommandException("syntax", "flicker reads 2t to 10s, not " + args.word("duration"));
        }
        Map<String, Region> regions = regions(context, args.has("regions") ? args.text("regions") : "");
        List<String> names = List.copyOf(regions.keySet());
        List<Double> drawn = new ArrayList<>();
        List<double[]> lumas = new ArrayList<>();
        CommandException[] failed = {null};
        Runnable reader = () -> {
            try (NativeImage image = FrameCapture.read()) {
                PixelStats.Frame frame = frameOf(image);
                double[] row = new double[names.size()];
                for (int index = 0; index < names.size(); index++) {
                    row[index] = PixelStats.of(frame, regions.get(names.get(index)).rect()).luma();
                }
                drawn.add(Screenshots.drawnAt());
                lumas.add(row);
            } catch (CommandException e) {
                failed[0] = e;
            } catch (RuntimeException e) {
                // This runs in the game's frame loop, where it would crash the game; it fails only the reading.
                Blockpilot.LOGGER.error("flicker could not read a frame", e);
                failed[0] = new CommandException("internal", "flicker " + e);
            }
        };
        Screenshots.everyFrame(reader);
        return new Action() {
            private int elapsed;

            @Override
            public Result tick() throws CommandException {
                if (failed[0] != null) {
                    cancel();
                    throw failed[0];
                }
                if (++elapsed < ticks) {
                    return null;
                }
                cancel();
                return flickerResult(names, drawn, lumas);
            }

            @Override
            public void cancel() {
                Screenshots.stopEveryFrame(reader);
            }

            @Override
            public String label() {
                return "flicker";
            }
        };
    }

    /** The frame as {@link PixelStats} reads it. */
    private static PixelStats.Frame frameOf(NativeImage image) {
        return new PixelStats.Frame() {
            @Override
            public int width() {
                return image.getWidth();
            }

            @Override
            public int height() {
                return image.getHeight();
            }

            @Override
            public int abgr(int x, int y) {
                return FrameCapture.abgr(image, x, y);
            }
        };
    }

    /** Each part's luma over the frames and its strongest rhythm, and a row per frame. */
    private static Result flickerResult(List<String> names, List<Double> drawn, List<double[]> lumas)
        throws CommandException {
        int count = drawn.size();
        if (count < 2) {
            throw new CommandException("no_frames", "the game drew " + count + " frame(s) meanwhile");
        }
        double[] times = drawn.stream().mapToDouble(Double::doubleValue).toArray();
        double span = (times[count - 1] - times[0]) / Duration.TICKS_PER_SECOND;
        double fps = span > 0 ? Math.round((count - 1) / span * 10) / 10.0 : 0;
        Fields fields = new Fields().put("frames", count, "frames=" + count)
            .put("fps", fps, "fps=" + Numbers.format(fps));
        for (int index = 0; index < names.size(); index++) {
            double[] values = new double[count];
            for (int frame = 0; frame < count; frame++) {
                values[frame] = lumas.get(frame)[index];
            }
            putRhythm(fields, names.get(index), values, times);
        }
        List<Row> rows = new ArrayList<>();
        for (int frame = 0; frame < count; frame++) {
            double drawnAt = Numbers.round(times[frame]);
            Fields row = new Fields().put("drawn", drawnAt, "drawn=" + Numbers.format(drawnAt));
            for (int index = 0; index < names.size(); index++) {
                double luma = Math.round(lumas.get(frame)[index] * 10) / 10.0;
                row.put(names.get(index), luma, names.get(index) + "=" + Numbers.format(luma));
            }
            rows.add(Row.of(String.valueOf(frame), row));
        }
        return Result.ok(fields).withRows(rows);
    }

    /** A part's luma over the frames, its min and max and its strongest rhythm, as values and as words. */
    private static void putRhythm(Fields fields, String name, double[] values, double[] times) {
        Map<String, Object> rhythm = new LinkedHashMap<>();
        StringJoiner text = new StringJoiner(" ", name + " ", "");
        putRounded(rhythm, text, "luma", mean(values), "");
        putRounded(rhythm, text, "min", Arrays.stream(values).min().orElse(0), "");
        putRounded(rhythm, text, "max", Arrays.stream(values).max().orElse(0), "");
        Flicker.Rhythm strongest = Flicker.strongest(times, values);
        if (strongest == null) {
            rhythm.put("flicker", "none");
            text.add("flicker=none");
        } else {
            putRounded(rhythm, text, "flicker", strongest.hertz(), "hz");
            putRounded(rhythm, text, "period", strongest.periodTicks(), "t");
            putRounded(rhythm, text, "amplitude", strongest.amplitude(), "");
        }
        fields.put(name, rhythm, text.toString());
    }

    /** A value to a tenth, under its name and in the words with its unit. */
    private static void putRounded(Map<String, Object> values, StringJoiner text, String name, double value,
        String unit) {
        double rounded = Math.round(value * 10) / 10.0;
        values.put(name, rounded);
        text.add(name + "=" + Numbers.format(rounded) + unit);
    }

    private static double mean(double[] values) {
        return Arrays.stream(values).average().orElse(0);
    }

    /** A part of the frame: as given, or around a point in the world as the last frame shows it. */
    @FunctionalInterface
    private interface Region {
        FrameRect rect() throws CommandException;
    }

    /** The regions by name, in order; a point's square reaches r= to each side, wherever r= comes. */
    private static Map<String, Region> regions(CommandContext context, String text) throws CommandException {
        Map<String, Function<Double, Region>> regions = new LinkedHashMap<>();
        double radius = DEFAULT_RADIUS;
        int unnamed = 0;
        for (String token : text.isBlank() ? new String[0] : text.strip().split("[ \t]+")) {
            if (token.startsWith("r=")) {
                String value = token.substring(2);
                if (!Lexicon.isSeconds(value) || Double.parseDouble(value) <= 0) {
                    throw new CommandException("syntax", "r= takes a positive number of blocks, got " + value);
                }
                radius = Double.parseDouble(value);
                continue;
            }
            int equals = token.indexOf('=');
            String name = equals > 0 ? token.substring(0, equals) : "r" + unnamed++;
            String body = token.substring(equals + 1);
            if (equals > 0 && !Lexicon.isIdentifier(name)) {
                throw new CommandException("syntax", "a region's name is an identifier, not " + name);
            }
            if (name.equals("camera")) {
                throw new CommandException("syntax", "camera is taken: the reply names a placed camera with it");
            }
            if (regions.containsKey(name)) {
                throw new CommandException("syntax", "two regions are named " + name);
            }
            if (body.startsWith("@")) {
                Position position = Position.parse(body.substring(1));
                if (position == null) {
                    throw new CommandException("syntax", "@ takes a position x,y,z or ~dx,dy,dz, not "
                        + body.substring(1));
                }
                if (Minecraft.getInstance().level == null) {
                    throw new CommandException("not_in_world");
                }
                Position at = context.resolve(position);
                double x = centre(at.x());
                double y = centre(at.y());
                double z = centre(at.z());
                regions.put(name, reach -> () -> project(name, x, y, z, reach));
            } else {
                FrameRect rect = FrameRect.parse(body);
                regions.put(name, reach -> () -> rect);
            }
        }
        if (regions.isEmpty()) {
            regions.put("all", reach -> () -> FrameRect.ALL);
            regions.put("centre", reach -> () -> FrameRect.CENTRE);
        }
        Map<String, Region> sized = new LinkedHashMap<>();
        double reach = radius;
        regions.forEach((name, region) -> sized.put(name, region.apply(reach)));
        return sized;
    }

    /** A whole coordinate names a block, whose centre is meant. */
    private static double centre(double coordinate) {
        return coordinate == Math.floor(coordinate) ? coordinate + 0.5 : coordinate;
    }

    private static FrameRect project(String name, double x, double y, double z, double reach) throws CommandException {
        Projection frame = camera;
        FrameRect part = frame == null ? null : frame.around(x, y, z, reach);
        if (part == null) {
            throw new CommandException("offscreen", name);
        }
        return part;
    }
}
