package dev.blockpilot.game.observe;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
//? if <1.21.11 {
import java.util.Comparator;
import net.minecraft.server.level.TicketType;
//?}

/**
 * {@code camera at … away}: the integrated server measures what it loads, ticks and sends for the player from the
 * camera instead. The client writes where its away camera is each tick; the server thread reads it through
 * {@link #presence}, which the server-side mixins use in place of the player's position.
 */
public final class AwayCamera {
    /** Where a player's away camera is, and in which dimension. */
    private record Spot(ResourceKey<Level> dimension, Vec3 position) {
    }

    private static final Map<UUID, Spot> SPOTS = new ConcurrentHashMap<>();
    //? if <1.21.11 {
    /** Keeps the player's own chunk entity-ticking while its tickets follow the camera, so the server ticks it. */
    private static final TicketType<ChunkPos> PLAYER_TICKING = TicketType.create("blockpilot_away",
        Comparator.comparingLong(ChunkPos::toLong));
    //?}
    /** The player this client's away camera belongs to, or null; client thread. */
    private static UUID owner;
    private static ResourceKey<Level> dimension;

    private AwayCamera() {
    }

    /** Takes the placed camera away from the player; client thread, after the placement. */
    static void start() {
        var minecraft = Minecraft.getInstance();
        end();
        owner = minecraft.player.getUUID();
        dimension = minecraft.level.dimension();
        write();
        recentre(owner, true);
    }

    /** The server measures from the player again, on every path that resets the camera; client thread. */
    public static void end() {
        if (owner == null) {
            return;
        }
        UUID player = owner;
        owner = null;
        SPOTS.remove(player);
        recentre(player, false);
    }

    public static boolean active() {
        return owner != null;
    }

    /** Where the server now centres the client's chunks: the away camera's place, else null. */
    static Vec3 centre() {
        Spot spot = owner == null ? null : SPOTS.get(owner);
        return spot == null ? null : spot.position();
    }

    /**
     * Where the client measures its surroundings from, the counterpart of {@link #presence}: its away camera's place
     * while one is placed, else where the player stands. Client thread, in a world.
     */
    public static Vec3 clientPresence() {
        Vec3 centre = centre();
        return centre != null ? centre : Minecraft.getInstance().player.position();
    }

    /** Each client tick: writes the camera's place for the server; a change of dimension ends it. */
    public static void tick() {
        if (owner == null) {
            return;
        }
        var level = Minecraft.getInstance().level;
        if (CameraControl.placement() == null || level == null || level.dimension() != dimension) {
            end();
            return;
        }
        write();
    }

    private static void write() {
        Vec3 at = CameraControl.placement().position().at(1);
        if (at != null) {
            SPOTS.put(owner, new Spot(dimension, at));
        }
    }

    /** Has the server follow at once instead of at its next tick, and holds or frees the player's own chunk. */
    private static void recentre(UUID player, boolean away) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(player);
            if (serverPlayer == null) {
                return;
            }
            ServerLevel level = level(serverPlayer);
            //? if <1.21.11 {
            ChunkPos own = serverPlayer.chunkPosition();
            if (away) {
                level.getChunkSource().addRegionTicket(PLAYER_TICKING, own, 2, own);
            } else {
                level.getChunkSource().removeRegionTicket(PLAYER_TICKING, own, 2, own);
            }
            //?}
            level.getChunkSource().move(serverPlayer);
        });
    }

    /** Server tick: every away player's tracking, loading and entities follow its camera. */
    public static void serverTick(MinecraftServer server) {
        for (UUID player : SPOTS.keySet()) {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(player);
            if (serverPlayer != null) {
                level(serverPlayer).getChunkSource().move(serverPlayer);
            }
        }
    }

    /** Whether the server takes the player to be at its away camera: one is placed, in the player's dimension. */
    public static boolean awayFrom(ServerPlayer player) {
        Spot spot = SPOTS.isEmpty() ? null : SPOTS.get(player.getUUID());
        return spot != null && spot.dimension() == player.level().dimension();
    }

    /** Where the server takes the player to be: its away camera in its own dimension, else where it stands. */
    public static Vec3 presence(ServerPlayer player) {
        Spot spot = SPOTS.isEmpty() ? null : SPOTS.get(player.getUUID());
        return spot != null && spot.dimension() == player.level().dimension() ? spot.position() : player.position();
    }

    public static SectionPos presenceSection(ServerPlayer player) {
        return SectionPos.of(presence(player));
    }

    public static ChunkPos presenceChunk(ServerPlayer player) {
        BlockPos at = BlockPos.containing(presence(player));
        return new ChunkPos(SectionPos.blockToSectionCoord(at.getX()), SectionPos.blockToSectionCoord(at.getZ()));
    }

    public static BlockPos presenceBlock(ServerPlayer player) {
        return BlockPos.containing(presence(player));
    }

    private static ServerLevel level(ServerPlayer player) {
        //? if >=1.21.11 {
        /*return player.level();
        *///?} else {
        return player.serverLevel();
        //?}
    }
}
