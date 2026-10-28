package dev.blockpilot.game.load;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.util.concurrent.TimeUnit;

/**
 * Holds every packet on the client's connection for the network load, in each direction, keeping their order. It
 * sits before Minecraft's packet handler, so it sees the packets the client receives and every one it sends, and
 * stays until the connection closes, passing packets straight on while the load is 0 and nothing is held.
 */
final class NetDelay extends ChannelDuplexHandler {
    private static final String NAME = "blockpilot_delay";
    private static final String PACKET_HANDLER = "packet_handler";

    private final Release received = new Release();
    private final Release sent = new Release();

    static void install(Channel channel) {
        if (channel.pipeline().get(NAME) == null) {
            channel.pipeline().addBefore(PACKET_HANDLER, NAME, new NetDelay());
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext context, Object message) {
        long wait = received.hold(System.nanoTime(), Load.netDelay());
        if (wait < 0) {
            context.fireChannelRead(message);
            return;
        }
        context.executor().schedule(() -> {
            received.released();
            context.fireChannelRead(message);
        }, wait, TimeUnit.NANOSECONDS);
    }

    @Override
    public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
        long wait = sent.hold(System.nanoTime(), Load.netDelay());
        if (wait < 0) {
            context.write(message, promise);
            return;
        }
        context.executor().schedule(() -> {
            sent.released();
            context.writeAndFlush(message, promise);
        }, wait, TimeUnit.NANOSECONDS);
    }

    /** Held writes flush as they go on, so a flush asked for meanwhile has nothing of its own to send. */
    @Override
    public void flush(ChannelHandlerContext context) {
        if (!sent.holding()) {
            context.flush();
        }
    }
}
