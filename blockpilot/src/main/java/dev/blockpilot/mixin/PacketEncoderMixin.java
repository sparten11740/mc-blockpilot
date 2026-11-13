package dev.blockpilot.mixin;

import dev.blockpilot.game.perf.PacketNames;
import dev.blockpilot.game.perf.Perf;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
//? if <1.21 {
/*import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
*///?}
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bytes per packet type sent over a real network connection, during a performance capture. */
@Mixin(PacketEncoder.class)
public abstract class PacketEncoderMixin {
    //? if <1.21 {
    /*@Shadow
    @Final
    private PacketFlow flow;

    *///?}
    @Inject(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Lio/netty/buffer/ByteBuf;)V",
        at = @At("RETURN"))
    private void blockpilot$encoded(ChannelHandlerContext context, Packet<?> packet, ByteBuf out, CallbackInfo info) {
        if (Perf.active) {
            //? if >=1.21 {
            boolean clientbound = packet.type().flow() == PacketFlow.CLIENTBOUND;
            //?} else {
            /*boolean clientbound = flow == PacketFlow.CLIENTBOUND;
            *///?}
            Perf.packetBytes(clientbound, PacketNames.of(packet), out.readableBytes());
        }
    }
}
