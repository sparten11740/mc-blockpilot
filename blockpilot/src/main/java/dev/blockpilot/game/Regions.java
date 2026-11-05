package dev.blockpilot.game;

import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import dev.blockpilot.game.common.Ids;
import dev.blockpilot.game.common.Sides;
import dev.blockpilot.game.sable.Rel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Region dumps for {@code blocks}: a palette of one-character symbols, then one section per Y layer (bottom up),
 * one row per Z (north to south), X west to east, runs of the same symbol written as {@code <count><symbol>}. The grid
 * form writes every cell's symbol instead of runs.
 */
final class Regions {
    private static final String SYMBOLS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private Regions() {
    }

    static Result dump(Level level, BlockPos min, BlockPos max) {
        Map<String, String> palette = new LinkedHashMap<>();
        List<Row> rows = new ArrayList<>();
        for (int y = min.getY(); y <= max.getY(); y++) {
            List<String> lines = new ArrayList<>();
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                StringBuilder line = new StringBuilder();
                String run = null;
                int length = 0;
                for (int x = min.getX(); x <= max.getX(); x++) {
                    String symbol = symbol(level, new BlockPos(x, y, z), palette);
                    if (symbol.equals(run)) {
                        length++;
                    } else {
                        append(line, run, length);
                        run = symbol;
                        length = 1;
                    }
                }
                append(line, run, length);
                lines.add(line.toString());
            }
            rows.add(Row.of("y" + y, new Fields().put("y", y, "y" + y + ":").put("rows", lines,
                String.join(" ", lines))));
        }
        return Result.ok(header(palette, min, max)).withRows(rows);
    }

    /** One row per Y layer (bottom up) and Z (north to south), one symbol per cell west to east. */
    static Result grid(Level level, BlockPos min, BlockPos max) {
        Map<String, String> palette = new LinkedHashMap<>();
        List<Row> rows = new ArrayList<>();
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                List<String> cells = new ArrayList<>();
                for (int x = min.getX(); x <= max.getX(); x++) {
                    cells.add(symbol(level, new BlockPos(x, y, z), palette));
                }
                rows.add(Row.of("y" + y + " z" + z, new Fields().put("y", y, "y" + y).put("z", z, "z" + z + ":")
                    .put("cells", cells, String.join(" ", cells))));
            }
        }
        return Result.ok(header(palette, min, max)).withRows(rows);
    }

    private static Fields header(Map<String, String> palette, BlockPos min, BlockPos max) {
        List<String> entries = new ArrayList<>();
        palette.forEach((state, symbol) -> entries.add(symbol + "=" + state));
        return new Fields().put("palette", palette, String.join(" ", entries)).hidden("from", Rel.absolute(min))
            .hidden("to", Rel.absolute(max));
    }

    private static String symbol(Level level, BlockPos pos, Map<String, String> palette) {
        if (!Sides.loaded(level, pos)) {
            return "?";
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return ".";
        }
        Map<String, String> properties = PerceptionCommands.properties(state);
        String key = Ids.of(state) + (properties.isEmpty() ? "" : properties.toString().replace(", ", ",")
            .replace("{", "[").replace("}", "]"));
        return palette.computeIfAbsent(key, ignored -> palette.size() < SYMBOLS.length()
            ? String.valueOf(SYMBOLS.charAt(palette.size())) : "#" + palette.size() + ";");
    }

    private static void append(StringBuilder line, String symbol, int length) {
        if (symbol == null) {
            return;
        }
        if (length > 2) {
            line.append(length).append(symbol);
        } else {
            line.append(symbol.repeat(length));
        }
    }
}
