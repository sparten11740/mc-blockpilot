package dev.blockpilot.game;

import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.Result;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * {@code side=server} on read-only queries: the same read against the integrated server's level instead of the
 * client's, on the server thread, so it sees chunks the client has not loaded (and never loads any).
 */
final class Sides {
    private static final long SERVER_READ_SECONDS = 5;

    /** A read that may fail with a command error. */
    @FunctionalInterface
    interface Read {
        Result apply(Level level, boolean server) throws CommandException;
    }

    private Sides() {
    }

    static Result read(Args args, Read read) throws CommandException {
        String side = args.option("side");
        Minecraft minecraft = Minecraft.getInstance();
        if (side == null || side.equals("client")) {
            return read.apply(minecraft.level, false);
        }
        if (!side.equals("server")) {
            throw new CommandException("syntax", "side=client or side=server");
        }
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            throw new CommandException("no_server");
        }
        if (!minecraft.player.hasPermissions(2)) {
            throw new CommandException("no_cheats");
        }
        ServerLevel level = server.getLevel(minecraft.level.dimension());
        try {
            return server.submit(() -> {
                try {
                    return read.apply(level, true);
                } catch (CommandException e) {
                    return e.toResult();
                }
            }).get(SERVER_READ_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CommandException("internal", "interrupted while reading from the server");
        } catch (ExecutionException e) {
            throw new CommandException("internal", "server read failed: " + e.getCause());
        } catch (TimeoutException e) {
            throw new CommandException("timeout", "the server did not answer within " + SERVER_READ_SECONDS + "s");
        }
    }
}
