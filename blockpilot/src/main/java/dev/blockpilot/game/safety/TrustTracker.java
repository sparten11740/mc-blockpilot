package dev.blockpilot.game.safety;

import dev.blockpilot.platform.ClientPlatform;
import dev.blockpilot.protocol.Connection;
import dev.blockpilot.protocol.SafetyPolicy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/** Keeps {@link Trust} in step with the connection, and judges the chat that comes in. */
public final class TrustTracker {
    private static final int REFRESH_TICKS = 20;

    private final Trust trust = new Trust();
    private final Supplier<SafetyPolicy> policy;
    private long ticks;

    public TrustTracker(Supplier<SafetyPolicy> policy) {
        this.policy = policy;
    }

    /** -Pblockpilot.trustUnverified, honoured only where the loader reports a development environment. */
    public static boolean trustUnverified() {
        String value = System.getProperty("blockpilot.trustUnverified");
        return ClientPlatform.developmentEnvironment() && value != null && !value.equals("false");
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
        return judged(message, sender, trust.connection());
    }

    /** The integrated server's log line for a player's chat, judged as the client judges it; server thread. */
    public Component judgeServer(Component content, UUID sender, String name) {
        return judged(content, sender, trust.connection().with(sender, name));
    }

    private Component judged(Component message, UUID sender, Connection now) {
        if (policy.get().trusts(sender, now)) {
            return message;
        }
        return Component.literal(Trust.withheld(sender, now.nameOf(sender)));
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
            //? if >=1.21.11 {
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
