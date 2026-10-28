package dev.blockpilot.mixin;

import dev.blockpilot.game.observe.EntityUpdates;
import dev.blockpilot.game.observe.EntityUpdates.Kind;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Counts the entity updates the client applies, for {@code entity}'s {@code net}. Each count follows the handler's
 * thread check, which hands a packet from the network thread to the client thread, so a packet counts once.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    private static final String ON_CLIENT_THREAD = "Lnet/minecraft/network/protocol/PacketUtils;"
        + "ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;"
        + "Lnet/minecraft/util/thread/BlockableEventLoop;)V";

    @Inject(method = "handleMoveEntity", at = @At(value = "INVOKE", target = ON_CLIENT_THREAD, shift = At.Shift.AFTER))
    private void blockpilot$moved(ClientboundMoveEntityPacket packet, CallbackInfo info) {
        EntityUpdates.CLIENT.count(((ClientboundMoveEntityPacketAccessor) packet).blockpilot$entityId(), Kind.MOVE);
    }

    @Inject(method = "handleTeleportEntity",
        at = @At(value = "INVOKE", target = ON_CLIENT_THREAD, shift = At.Shift.AFTER))
    private void blockpilot$teleported(ClientboundTeleportEntityPacket packet, CallbackInfo info) {
        EntityUpdates.CLIENT.count(packet.getId(), Kind.TELEPORT);
    }

    @Inject(method = "handleSetEntityMotion",
        at = @At(value = "INVOKE", target = ON_CLIENT_THREAD, shift = At.Shift.AFTER))
    private void blockpilot$motion(ClientboundSetEntityMotionPacket packet, CallbackInfo info) {
        EntityUpdates.CLIENT.count(packet.getId(), Kind.MOTION);
    }

    @Inject(method = "handleSetEntityData",
        at = @At(value = "INVOKE", target = ON_CLIENT_THREAD, shift = At.Shift.AFTER))
    private void blockpilot$data(ClientboundSetEntityDataPacket packet, CallbackInfo info) {
        EntityUpdates.CLIENT.count(packet.id(), Kind.DATA);
    }
}
