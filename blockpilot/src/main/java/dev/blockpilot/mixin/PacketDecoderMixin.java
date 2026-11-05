package dev.blockpilot.mixin;

import dev.blockpilot.game.perf.PacketNames;
import dev.blockpilot.game.perf.Perf;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import java.util.List;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bytes per packet type received over a real network connection, during a performance capture. */
@Mixin(PacketDecoder.class)
public abstract class PacketDecoderMixin {
    @Unique
    private int blockpilot$readable;

    @Inject(method = "decode", at = @At("HEAD"))
    private void blockpilot$before(ChannelHandlerContext context, ByteBuf in, List<Object> out, CallbackInfo info) {
        blockpilot$readable = Perf.active ? in.readableBytes() : 0;
    }

    @Inject(method = "decode", at = @At("RETURN"))
    private void blockpilot$decoded(ChannelHandlerContext context, ByteBuf in, List<Object> out, CallbackInfo info) {
        if (Perf.active && !out.isEmpty() && out.get(out.size() - 1) instanceof Packet<?> packet) {
            Perf.packetBytes(packet.type().flow() == PacketFlow.CLIENTBOUND, PacketNames.of(packet),
                blockpilot$readable - in.readableBytes());
        }
    }
}
