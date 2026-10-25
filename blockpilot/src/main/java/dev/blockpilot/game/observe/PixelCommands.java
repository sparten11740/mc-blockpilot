package dev.blockpilot.game.observe;

import com.mojang.blaze3d.platform.NativeImage;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Lexicon;
import dev.blockpilot.api.Numbers;
import dev.blockpilot.api.Position;
import dev.blockpilot.api.Result;
import dev.blockpilot.game.FrameCapture;
import dev.blockpilot.protocol.CommandRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** {@code pixels}: colour and brightness of parts of the last rendered frame, for checks on what was drawn. */
public final class PixelCommands {
    private static final double DEFAULT_RADIUS = 0.5;
    private static volatile Projection camera;

    /** Called by the renderer with the matrices of the frame it is about to draw. */
    public static void recordFrame(Vec3 position, Matrix4f view, Matrix4f projection) {
        camera = new Projection(position.x, position.y, position.z, view.get(new float[16]),
            projection.get(new float[16]));
    }

    public void register(CommandRegistry registry) {
        registry.register(CommandSpec.query("pixels", this::pixels).group("observe")
            .form(form -> form.optionalText("regions"))
            .help("measures parts of the last rendered frame (GUI included unless hidden): [name=]x0,y0,x1,y1 in "
                + "frame fractions from the top left, or [name=]@<pos>, the square around a world position reaching "
                + "r=<blocks> to each side (default 0.5); unnamed ones are r0, r1, …, and with none all and centre. "
                + "Each gives <name>.r .g .b .luma (means), .max and .p90 (luma), 0-255; err offscreen <name> when a "
                + "position is behind the camera or out of the frame")
            .example("pixels sky=0.4,0.05,0.6,0.15 @~0,0,4 r=1"));
    }

    private Result pixels(CommandContext context, Args args) throws CommandException {
        Map<String, Object> regions = regions(context, args.has("regions") ? args.text("regions") : "");
        try (NativeImage image = FrameCapture.read()) {
            PixelStats.Frame frame = new PixelStats.Frame() {
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
            Fields fields = new Fields();
            for (Map.Entry<String, Object> region : regions.entrySet()) {
                FrameRect part = region.getValue() instanceof FrameRect rect ? rect
                    : project(region.getKey(), (double[]) region.getValue());
                Map<String, Object> stats = PixelStats.of(frame, part).asMap();
                fields.put(region.getKey(), stats, region.getKey() + " rgb=" + Numbers.format((double) stats.get("r"))
                    + "," + Numbers.format((double) stats.get("g")) + "," + Numbers.format((double) stats.get("b"))
                    + " luma=" + Numbers.format((double) stats.get("luma")) + " max=" + stats.get("max") + " p90="
                    + stats.get("p90"));
            }
            return Result.ok(fields);
        }
    }

    /** The regions by name: a frame part, or the world point and radius {x, y, z, r} to project. */
    private static Map<String, Object> regions(CommandContext context, String text) throws CommandException {
        Map<String, Object> regions = new LinkedHashMap<>();
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
                regions.put(name, new double[] {centre(at.x()), centre(at.y()), centre(at.z())});
            } else {
                regions.put(name, FrameRect.parse(body));
            }
        }
        if (regions.isEmpty()) {
            regions.put("all", FrameRect.ALL);
            regions.put("centre", FrameRect.CENTRE);
        }
        double finalRadius = radius;
        regions.replaceAll((name, region) -> region instanceof double[] point
            ? new double[] {point[0], point[1], point[2], finalRadius} : region);
        return regions;
    }

    /** A whole coordinate names a block, whose centre is meant. */
    private static double centre(double coordinate) {
        return coordinate == Math.floor(coordinate) ? coordinate + 0.5 : coordinate;
    }

    private static FrameRect project(String name, double[] point) throws CommandException {
        Projection frame = camera;
        FrameRect part = frame == null ? null : frame.around(point[0], point[1], point[2], point[3]);
        if (part == null) {
            throw new CommandException("offscreen", name);
        }
        return part;
    }
}
