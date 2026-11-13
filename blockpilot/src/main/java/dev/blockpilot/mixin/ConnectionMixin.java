package dev.blockpilot.mixin;

import dev.blockpilot.game.perf.PacketNames;
import dev.blockpilot.game.perf.Perf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
//? if >=26.1 {
/*import io.netty.channel.ChannelFutureListener;
*///?} else {
import net.minecraft.network.PacketSendListener;
//?}
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Counts packets per type during a performance capture, on the client's own connection: what it receives is
 * client-bound, what it sends server-bound. That covers singleplayer and remote servers alike. A bundle counts as
 * one and its packets each by their own type.
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
            if (packet instanceof BundlePacket<?> bundle) {
                for (Packet<?> part : bundle.subPackets()) {
                    Perf.packet(true, PacketNames.of(part));
                }
            }
        }
    }

    //? if >=26.1 {
    /*@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
        at = @At("HEAD"))
    private void blockpilot$sent(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo info) {
    *///?} else if >=1.21 {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Z)V",
        at = @At("HEAD"))
    private void blockpilot$sent(Packet<?> packet, PacketSendListener listener, boolean flush, CallbackInfo info) {
    //?} else {
    /*@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
        at = @At("HEAD"))
    private void blockpilot$sent(Packet<?> packet, PacketSendListener listener, CallbackInfo info) {
    *///?}
        if (Perf.active && getReceiving() == PacketFlow.CLIENTBOUND) {
            Perf.packet(false, PacketNames.of(packet));
        }
    }
}
