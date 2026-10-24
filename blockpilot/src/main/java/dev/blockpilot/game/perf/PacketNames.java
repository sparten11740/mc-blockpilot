package dev.blockpilot.game.perf;

import dev.blockpilot.game.Ids;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/** A packet's type as reported in performance captures; custom payloads add their payload id. */
public final class PacketNames {
    private PacketNames() {
    }

    public static String of(Packet<?> packet) {
        String type = Ids.of(packet.type().id());
        if (packet instanceof ClientboundCustomPayloadPacket custom) {
            return type + "/" + Ids.of(custom.payload().type().id());
        }
        if (packet instanceof ServerboundCustomPayloadPacket custom) {
            return type + "/" + Ids.of(custom.payload().type().id());
        }
        return type;
    }
}
