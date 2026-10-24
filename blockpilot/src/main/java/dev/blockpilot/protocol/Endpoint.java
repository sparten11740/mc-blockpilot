package dev.blockpilot.protocol;

import dev.blockpilot.api.BlockpilotApi;
import dev.blockpilot.api.Event;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The single TCP port agents connect to. The first bytes of a connection decide its protocol: an HTTP request line
 * means MCP over HTTP, anything else is the line protocol. Network I/O runs on its own thread and never on the game's.
 */
public final class Endpoint {
    private static final Logger LOGGER = LoggerFactory.getLogger("Blockpilot");
    private static final int MAX_LINE = 1024 * 1024;
    private static final List<String> HTTP_METHODS = List.of("GET ", "POST", "PUT ", "DELE", "OPTI", "HEAD", "PATC");

    /** Identifies the build in the line protocol's {@code auth} reply. */
    public record Identity(String modVersion, String minecraftVersion, String loader) {
        String greeting() {
            return "blockpilot " + modVersion + " api" + BlockpilotApi.VERSION + " mc" + minecraftVersion + " " + loader;
        }
    }

    private final Engine engine;
    private final Token token;
    private final McpServer mcp;
    private final Identity identity;
    private EventLoopGroup group;
    private Channel channel;

    public Endpoint(Engine engine, Token token, McpServer mcp, Identity identity) {
        this.engine = engine;
        this.token = token;
        this.mcp = mcp;
        this.identity = identity;
    }

    /**
     * Binds the first free port of {@code port} … {@code port + tries - 1}, so several game instances on one machine
     * each get an endpoint; {@code endpoint.json} tells clients which one. Throws when none is free.
     */
    public void start(String bindAddress, int port, int tries) throws Exception {
        Exception last = null;
        for (int candidate = port; candidate < port + Math.max(1, tries); candidate++) {
            try {
                bind(bindAddress, candidate);
                return;
            } catch (Exception e) {
                last = e;
                LOGGER.info("Could not bind port {} ({}); trying the next one", candidate, e.getMessage());
            }
        }
        throw last;
    }

    private void bind(String bindAddress, int port) throws Exception {
        ThreadFactory threads = runnable -> {
            Thread thread = new Thread(runnable, "Blockpilot endpoint");
            thread.setDaemon(true);
            return thread;
        };
        group = new NioEventLoopGroup(1, threads);
        try {
            channel = new ServerBootstrap().group(group).channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socket) {
                        socket.pipeline().addLast(new ProtocolSniffer());
                    }
                })
                .bind(InetAddress.getByName(bindAddress), port).sync().channel();
        } catch (Exception e) {
            group.shutdownGracefully();
            group = null;
            throw e;
        }
        LOGGER.info("Blockpilot endpoint listening on {}:{}", bindAddress, port);
    }

    public int port() {
        return ((InetSocketAddress) channel.localAddress()).getPort();
    }

    public void stop() {
        mcp.closeAll();
        if (channel != null) {
            channel.close().syncUninterruptibly();
        }
        if (group != null) {
            group.shutdownGracefully();
        }
    }

    /** Looks at the first bytes and installs the matching protocol's handlers. */
    private final class ProtocolSniffer extends ByteToMessageDecoder {
        @Override
        protected void decode(ChannelHandlerContext context, ByteBuf in, List<Object> out) {
            if (in.readableBytes() < 4 && in.indexOf(in.readerIndex(), in.writerIndex(), (byte) '\n') < 0) {
                return;
            }
            String start = in.toString(in.readerIndex(), Math.min(4, in.readableBytes()), StandardCharsets.US_ASCII);
            if (HTTP_METHODS.contains(start)) {
                context.pipeline().addLast(new HttpCodec.Decoder(), new HttpConnection());
            } else {
                context.pipeline().addLast(new LineBasedFrameDecoder(MAX_LINE),
                    new StringDecoder(StandardCharsets.UTF_8), new LineConnection());
            }
            context.pipeline().remove(this);
        }
    }

    /** One line-protocol client: {@code auth <token>}, then one request per line. */
    private final class LineConnection extends SimpleChannelInboundHandler<String> {
        private Session session;
        private CompletableFuture<?> previous = CompletableFuture.completedFuture(null);

        @Override
        protected void channelRead0(ChannelHandlerContext context, String line) {
            String request = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
            if (session == null) {
                authenticate(context, request);
                return;
            }
            String id = null;
            String script = request;
            if (request.startsWith("#")) {
                int space = request.indexOf(' ');
                id = space < 0 ? request.substring(1) : request.substring(1, space);
                script = space < 0 ? "" : request.substring(space + 1);
            }
            CompletableFuture<Reply> reply = engine.submit(session, id, script);
            // Replies go out in request order, even when a later query finishes before an earlier action.
            previous = previous.thenCompose(ignored -> reply).thenAccept(answer -> write(context, answer));
        }

        private void authenticate(ChannelHandlerContext context, String request) {
            if (request.startsWith("auth ") && token.matches(request.substring(5))) {
                session = engine.openSession();
                Session opened = session;
                Channel connection = context.channel();
                session.setPushTarget(event -> connection.writeAndFlush(Unpooled.copiedBuffer(
                    eventLine(event, opened.format()) + "\n", StandardCharsets.UTF_8)));
                Reply greeting = new Reply(null, List.of(Result.ok(new Fields().bare("server", identity.greeting()))),
                    List.of());
                write(context, greeting);
            } else {
                context.writeAndFlush(Unpooled.copiedBuffer("err auth\n.\n", StandardCharsets.UTF_8))
                    .addListener(ChannelFutureListener.CLOSE);
            }
        }

        private void write(ChannelHandlerContext context, Reply reply) {
            String body = session.format() == Session.Format.JSON ? Formatter.json(reply) + "\n"
                : Formatter.text(reply) + "\n.\n";
            context.writeAndFlush(Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
        }

        @Override
        public void channelInactive(ChannelHandlerContext context) {
            if (session != null) {
                engine.closeSession(session);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
            LOGGER.debug("Line connection failed", cause);
            context.close();
        }
    }

    /** One HTTP connection; each request is answered by the MCP server, in order. */
    private final class HttpConnection extends SimpleChannelInboundHandler<HttpCodec.Request> {
        private CompletableFuture<?> previous = CompletableFuture.completedFuture(null);

        @Override
        protected void channelRead0(ChannelHandlerContext context, HttpCodec.Request request) {
            CompletableFuture<HttpCodec.Response> response = mcp.handle(request);
            boolean close = "close".equalsIgnoreCase(request.header("connection"));
            previous = previous.thenCompose(ignored -> response).thenAccept(answer -> {
                var future = context.writeAndFlush(Unpooled.wrappedBuffer(answer.encode()));
                if (close) {
                    future.addListener(ChannelFutureListener.CLOSE);
                }
            });
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
            LOGGER.debug("HTTP connection failed", cause);
            context.writeAndFlush(Unpooled.wrappedBuffer(HttpCodec.Response.of(400, "text/plain",
                String.valueOf(cause.getMessage())).encode())).addListener(ChannelFutureListener.CLOSE);
        }
    }

    /** A pushed event as one line in the session's format. */
    static String eventLine(Event event, Session.Format format) {
        return format == Session.Format.JSON ? Formatter.json(event).toString() : event.text();
    }
}
