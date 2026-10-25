package dev.blockpilot.mixin;

import dev.blockpilot.game.perf.PacketNames;
import dev.blockpilot.game.perf.Perf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Counts packets per type during a performance capture, on the client's own connection: what it receives is
 * client-bound, what it sends server-bound. That covers singleplayer and remote servers alike.
 */
@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Shadow
    public abstract PacketFlow getReceiving();

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
        at = @At("HEAD"))
    private void blockpilot$received(ChannelHandlerContext context, Packet<?> packet, CallbackInfo info) {
        if (Perf.active && getReceiving() == PacketFlow.CLIENTBOUND) {
            Perf.packet(true, PacketNames.of(packet));
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
        at = @At("HEAD"))
    private void blockpilot$sent(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo info) {
        if (Perf.active && getReceiving() == PacketFlow.CLIENTBOUND) {
            Perf.packet(false, PacketNames.of(packet));
        }
    }
}
