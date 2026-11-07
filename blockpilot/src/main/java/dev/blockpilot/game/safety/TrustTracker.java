package dev.blockpilot.game.safety;

import dev.blockpilot.platform.ClientPlatform;
import dev.blockpilot.protocol.Config;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/** Keeps {@link Trust} in step with the connection, and judges the chat that comes in. */
public final class TrustTracker {
    private static final int REFRESH_TICKS = 20;

    private final Trust trust;
    private long ticks;

    public TrustTracker(Config config) {
        String trustUnverified = System.getProperty("blockpilot.trustUnverified");
        this.trust = new Trust(config.trustedPlayers, ClientPlatform.developmentEnvironment()
            && trustUnverified != null && !trustUnverified.equals("false"));
    }

    public Trust trust() {
        return trust;
    }

    public void tick() {
        if (ticks++ % REFRESH_TICKS == 0) {
            refresh();
        }
    }

    /** What the chat window shows for a chat line: the line, or the stand-in for an untrusted player's. */
    public Component judge(Component message, UUID sender) {
        if (sender == null) {
            return message;
        }
        refresh();
        String withheld = trust.withheld(sender, trust.nameOf(sender));
        return withheld == null ? message : Component.literal(withheld);
    }

    /** The integrated server's log line for a player's chat, judged as the client judges it; server thread. */
    public Component judgeServer(Component content, UUID sender, String name) {
        String withheld = trust.withheld(sender, name);
        return withheld == null ? content : Component.literal(withheld);
    }

    private void refresh() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft != null ? minecraft.player : null;
        ClientPacketListener connection = minecraft != null ? minecraft.getConnection() : null;
        if (player == null || connection == null) {
            trust.update(null, false, Map.of());
            return;
        }
        Map<UUID, String> players = new HashMap<>();
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            //? if >=26.1 {
            /*players.put(info.getProfile().id(), info.getProfile().name());
            *///?} else {
            players.put(info.getProfile().getId(), info.getProfile().getName());
            //?}
        }
        MinecraftServer server = minecraft.getSingleplayerServer();
        boolean unverified = server != null
            ? !server.usesAuthentication()
            : Trust.offlineAccount(player.getUUID(), player.getName().getString());
        trust.update(player.getUUID(), unverified, players);
    }
}
