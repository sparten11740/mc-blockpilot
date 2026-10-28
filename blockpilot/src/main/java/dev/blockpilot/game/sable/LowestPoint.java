package dev.blockpilot.game.sable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3i;
import org.joml.Vector3ic;

/**
 * A sub-level's lowest point as it lies now: the lowest corner of the block aboard that lies lowest in the world. A
 * plot point's world height grows by {@code up · step} for every step through the plot, where {@code up} is world up
 * seen in the plot, so that block is the one with the least {@code up · pos}.
 */
final class LowestPoint {
    /** Keeps a bound that ends on a block edge from taking in the next block. */
    private static final double EDGE = 1.0E-6;

    /** Blocks from min to max, corners included. */
    record Part(Vector3ic min, Vector3ic max, Solid solid) {
    }

    /** Whether the block isn't air. */
    @FunctionalInterface
    interface Solid {
        boolean at(int x, int y, int z);
    }

    private LowestPoint() {
    }

    /** The world point, or null when the level holds none of the sub-level's blocks. */
    static Vec3 of(Level level, SubLevels.Info info) {
        SubLevels subLevels = SableSupport.subLevels();
        Vec3 origin = subLevels.toPlot(level, info.id(), info.position());
        Vec3 above = subLevels.toPlot(level, info.id(), info.position().add(0, 1, 0));
        if (origin == null || above == null) {
            return null;
        }
        Vector3d up = new Vector3d(above.x - origin.x, above.y - origin.y, above.z - origin.z);
        Vector3i block = lowestBlock(parts(level, info.plotBounds()), up);
        if (block == null) {
            return null;
        }
        // A corner on the plot's edge would lie outside it, where toWorld leaves points as they are.
        Vector3d inside = corner(block, up).lerp(new Vector3d(block).add(0.5, 0.5, 0.5), EDGE);
        return subLevels.toWorld(level, new Vec3(inside.x, inside.y, inside.z));
    }

    /**
     * The block with the least {@code up · pos}, or null when there is none. Parts are read from the one that could
     * reach lowest, and the search stops at the first that can't beat the block found, so it reads few of them.
     */
    static Vector3i lowestBlock(List<Part> parts, Vector3dc up) {
        List<Part> ordered = new ArrayList<>(parts);
        ordered.sort(Comparator.comparingDouble(part -> reach(part, up)));
        Vector3i lowest = null;
        double least = Double.POSITIVE_INFINITY;
        for (Part part : ordered) {
            if (reach(part, up) >= least) {
                break;
            }
            Vector3i found = lowestIn(part, up, least);
            if (found != null) {
                lowest = found;
                least = height(found, up);
            }
        }
        return lowest;
    }

    /** The block's lowest corner: its far side along each axis that runs downward. */
    static Vector3d corner(Vector3ic block, Vector3dc up) {
        return new Vector3d(block.x() + (up.x() < 0 ? 1 : 0), block.y() + (up.y() < 0 ? 1 : 0),
            block.z() + (up.z() < 0 ? 1 : 0));
    }

    /** The least {@code up · pos} any of the part's blocks could have: at its corner block furthest down. */
    static double reach(Part part, Vector3dc up) {
        return up.x() * (up.x() < 0 ? part.max().x() : part.min().x())
            + up.y() * (up.y() < 0 ? part.max().y() : part.min().y())
            + up.z() * (up.z() < 0 ? part.max().z() : part.min().z());
    }

    /** The part's lowest block below {@code least}, or null. */
    private static Vector3i lowestIn(Part part, Vector3dc up, double least) {
        Vector3i lowest = null;
        for (int y = part.min().y(); y <= part.max().y(); y++) {
            for (int x = part.min().x(); x <= part.max().x(); x++) {
                for (int z = part.min().z(); z <= part.max().z(); z++) {
                    double height = up.x() * x + up.y() * y + up.z() * z;
                    if (height < least && part.solid().at(x, y, z)) {
                        lowest = new Vector3i(x, y, z);
                        least = height;
                    }
                }
            }
        }
        return lowest;
    }

    private static double height(Vector3ic block, Vector3dc up) {
        return up.x() * block.x() + up.y() * block.y() + up.z() * block.z();
    }

    /** The loaded sections in the plot bounds that hold more than air, each cut to the bounds. */
    private static List<Part> parts(Level level, AABB plot) {
        int minX = Mth.floor(plot.minX);
        int maxX = Mth.floor(plot.maxX - EDGE);
        int minY = Math.max(level.getMinBuildHeight(), Mth.floor(plot.minY));
        int maxY = Math.min(level.getMaxBuildHeight() - 1, Mth.floor(plot.maxY - EDGE));
        int minZ = Mth.floor(plot.minZ);
        int maxZ = Mth.floor(plot.maxZ - EDGE);
        List<Part> parts = new ArrayList<>();
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                LevelChunkSection[] sections = chunk.getSections();
                for (int index = 0; index < sections.length; index++) {
                    LevelChunkSection section = sections[index];
                    int sectionY = (chunk.getMinSection() + index) << 4;
                    if (section.hasOnlyAir() || sectionY + 15 < minY || sectionY > maxY) {
                        continue;
                    }
                    Vector3i from = new Vector3i(Math.max(minX, chunkX << 4), Math.max(minY, sectionY),
                        Math.max(minZ, chunkZ << 4));
                    Vector3i to = new Vector3i(Math.min(maxX, (chunkX << 4) + 15), Math.min(maxY, sectionY + 15),
                        Math.min(maxZ, (chunkZ << 4) + 15));
                    parts.add(new Part(from, to, (x, y, z) -> !section.getBlockState(x & 15, y & 15, z & 15).isAir()));
                }
            }
        }
        return parts;
    }
}
