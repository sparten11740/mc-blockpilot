package dev.blockpilot.game.common;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * The one tick number every reply uses ({@code tick query}, {@code sample} rows, waits, the sound, message and
 * particle logs and their {@code since=}): the integrated server's game time in singleplayer, otherwise the client
 * level's. The client's own copy lags the server's by a tick or more, so mixing the two made {@code since=} miss.
 */
public final class GameClock {
    private GameClock() {
    }

    /** The current tick, or -1 outside a world. */
    public static long now() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return -1;
        }
        MinecraftServer server = minecraft.getSingleplayerServer();
        ServerLevel overworld = server == null ? null : server.overworld();
        return overworld != null ? overworld.getGameTime() : minecraft.level.getGameTime();
    }
}
