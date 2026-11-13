package dev.blockpilot.game.perf;

import dev.blockpilot.game.common.Ids;
import net.minecraft.network.protocol.Packet;
//? if >=1.21 {
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
//?} else {
/*import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
*///?}

/** A packet's type as reported in performance captures; custom payloads add their payload id. */
public final class PacketNames {
    private PacketNames() {
    }

    public static String of(Packet<?> packet) {
        //? if >=1.21 {
        String type = Ids.of(packet.type().id());
        if (packet instanceof ClientboundCustomPayloadPacket custom) {
            return type + "/" + Ids.of(custom.payload().type().id());
        }
        if (packet instanceof ServerboundCustomPayloadPacket custom) {
            return type + "/" + Ids.of(custom.payload().type().id());
        }
        //?} else {
        /*String type = OldPacketIds.of(packet);
        if (packet instanceof ClientboundCustomPayloadPacket custom) {
            return type + "/" + Ids.of(custom.getIdentifier());
        }
        if (packet instanceof ServerboundCustomPayloadPacket custom) {
            return type + "/" + Ids.of(custom.getIdentifier());
        }
        *///?}
        return type;
    }
}
