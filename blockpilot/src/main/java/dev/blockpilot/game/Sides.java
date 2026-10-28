package dev.blockpilot.game;

import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.Result;
import dev.blockpilot.protocol.CommandFailure;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
//? if >=26.1 {
/*import net.minecraft.server.permissions.Permissions;
*///?}
import net.minecraft.world.level.Level;

/**
 * {@code side=server} on read-only queries: the same read against the integrated server's level instead of the
 * client's, on the server thread, so it sees chunks the client has not loaded (and never loads any). Client-thread
 * actions also do their one server step through it.
 */
public final class Sides {
    public static final List<String> NAMES = List.of("client", "server");
    private static final long SERVER_READ_SECONDS = 5;

    /** A read that may fail with a command error. */
    @FunctionalInterface
    public interface Read {
        Result apply(Level level, boolean server) throws CommandException;
    }

    private Sides() {
    }

    /**
     * Whether the chunk holding the position is loaded on that side; never loads or generates it. Call it on that
     * side's thread: off its thread the server's chunk cache answers null. (On the client, {@code Level.hasChunkAt}
     * answers true for every chunk.)
     */
    public static boolean loaded(Level level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()),
            SectionPos.blockToSectionCoord(pos.getZ())) != null;
    }

    public static Result read(Args args, Read read) throws CommandException {
        String side = args.option("side");
        if (side == null || side.equals("client")) {
            return read.apply(Minecraft.getInstance().level, false);
        }
        return onServer(read);
    }

    /**
     * Runs on the integrated server's level in the player's dimension, on the server thread, and waits for it up to
     * the usual limit; its command errors come back as error results.
     */
    public static Result onServer(Read read) throws CommandException {
        Minecraft minecraft = Minecraft.getInstance();
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            throw new CommandException("no_server");
        }
        if (!cheats(minecraft.player)) {
            throw new CommandException("no_cheats");
        }
        ServerLevel level = server.getLevel(minecraft.level.dimension());
        Supplier<Result> task = () -> {
            try {
                return read.apply(level, true);
            } catch (CommandException e) {
                return e.toResult();
            } catch (CommandFailure e) {
                return e.toResult();
            }
        };
        // On the server thread, inside a task it runs, a task submitted now would wait behind the one running.
        return server.isSameThread() ? task.get() : await(server.submit(task));
    }

    /** Waits for a task submitted to the server thread, up to the usual limit. */
    static <T> T await(CompletableFuture<T> task) throws CommandException {
        try {
            return task.get(SERVER_READ_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CommandException("internal", "interrupted while reading from the server");
        } catch (ExecutionException e) {
            throw new CommandException("internal", "server read failed: " + e.getCause());
        } catch (TimeoutException e) {
            throw new CommandException("timeout", "the server did not answer within " + SERVER_READ_SECONDS + "s");
        }
    }

    /**
     * A read on the integrated server's level, on its thread, for a note beside a client read: null without a
     * singleplayer server or cheats, and null when it fails or takes too long, so the client's answer never waits on
     * it for more than the usual limit.
     */
    public static <T> T tryServer(Function<Level, T> read) {
        Minecraft minecraft = Minecraft.getInstance();
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null || !cheats(minecraft.player)) {
            return null;
        }
        ServerLevel level = server.getLevel(minecraft.level.dimension());
        if (level == null) {
            return null;
        }
        if (server.isSameThread()) {
            return read.apply(level);
        }
        try {
            return server.submit(() -> read.apply(level)).get(SERVER_READ_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException | TimeoutException e) {
            return null;
        }
    }

    /** Whether the player may use cheats (command level 2), which reads on the integrated server need. */
    static boolean cheats(LocalPlayer player) {
        //? if >=26.1 {
        /*return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        *///?} else {
        return player.hasPermissions(2);
        //?}
    }
}
