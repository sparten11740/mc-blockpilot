package dev.blockpilot.mixin;

import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The entity a move packet is for, which the packet only hands out by looking the entity up. */
@Mixin(ClientboundMoveEntityPacket.class)
public interface ClientboundMoveEntityPacketAccessor {
    @Accessor("entityId")
    int blockpilot$entityId();
}
