package dev.blockpilot.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.TooLongFrameException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Just enough HTTP/1.1 for MCP: request line, headers and a {@code Content-Length} body, with keep-alive. Minecraft
 * ships Netty without its HTTP codec, so this stands in for it.
 */
public final class HttpCodec {
    static final int MAX_HEADER_BYTES = 16 * 1024;
    static final int MAX_BODY_BYTES = 1024 * 1024;

    /** A parsed request; header names are lower-case. */
    public record Request(String method, String path, Map<String, String> headers, String body) {
        public String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }

    /** A response to write. */
    public record Response(int status, String contentType, String body, Map<String, String> headers) {
        public static Response of(int status, String contentType, String body) {
            return new Response(status, contentType, body, new LinkedHashMap<>());
        }

        public Response header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        public byte[] encode() {
            byte[] content = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            StringBuilder head = new StringBuilder("HTTP/1.1 ").append(status).append(' ').append(reason(status))
                .append("\r\n");
            if (contentType != null) {
                head.append("Content-Type: ").append(contentType).append("\r\n");
            }
            head.append("Content-Length: ").append(content.length).append("\r\n");
            headers.forEach((name, value) -> head.append(name).append(": ").append(value).append("\r\n"));
            head.append("\r\n");
            byte[] headBytes = head.toString().getBytes(StandardCharsets.US_ASCII);
            byte[] out = new byte[headBytes.length + content.length];
            System.arraycopy(headBytes, 0, out, 0, headBytes.length);
            System.arraycopy(content, 0, out, headBytes.length, content.length);
            return out;
        }

        private static String reason(int status) {
            return switch (status) {
                case 200 -> "OK";
                case 202 -> "Accepted";
                case 204 -> "No Content";
                case 400 -> "Bad Request";
                case 401 -> "Unauthorized";
                case 403 -> "Forbidden";
                case 404 -> "Not Found";
                case 405 -> "Method Not Allowed";
                case 411 -> "Length Required";
                case 413 -> "Payload Too Large";
                default -> "Error";
            };
        }
    }

    /** Turns bytes into {@link Request}s, one per complete request. */
    public static final class Decoder extends ByteToMessageDecoder {
        @Override
        protected void decode(ChannelHandlerContext context, ByteBuf in, List<Object> out) {
            int headerEnd = indexOf(in, in.readerIndex(), "\r\n\r\n");
            if (headerEnd < 0) {
                if (in.readableBytes() > MAX_HEADER_BYTES) {
                    throw new TooLongFrameException("HTTP headers too long");
                }
                return;
            }
            String head = in.toString(in.readerIndex(), headerEnd - in.readerIndex(), StandardCharsets.ISO_8859_1);
            String[] lines = head.split("\r\n");
            String[] requestLine = lines[0].split(" ");
            if (requestLine.length < 2) {
                throw new IllegalArgumentException("Malformed request line");
            }
            Map<String, String> headers = new LinkedHashMap<>();
            for (int index = 1; index < lines.length; index++) {
                int colon = lines[index].indexOf(':');
                if (colon > 0) {
                    headers.put(lines[index].substring(0, colon).strip().toLowerCase(Locale.ROOT),
                        lines[index].substring(colon + 1).strip());
                }
            }
            if (headers.containsKey("transfer-encoding")) {
                throw new IllegalArgumentException("Chunked request bodies are not supported");
            }
            int length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
            if (length > MAX_BODY_BYTES) {
                throw new TooLongFrameException("HTTP body too large");
            }
            int bodyStart = headerEnd + 4;
            if (in.writerIndex() - bodyStart < length) {
                return;
            }
            String body = in.toString(bodyStart, length, StandardCharsets.UTF_8);
            in.readerIndex(bodyStart + length);
            out.add(new Request(requestLine[0], requestLine[1], headers, body));
        }

        private static int indexOf(ByteBuf buffer, int from, String needle) {
            byte[] bytes = needle.getBytes(StandardCharsets.US_ASCII);
            for (int index = from; index <= buffer.writerIndex() - bytes.length; index++) {
                boolean match = true;
                for (int offset = 0; offset < bytes.length; offset++) {
                    if (buffer.getByte(index + offset) != bytes[offset]) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return index;
                }
            }
            return -1;
        }
    }

    private HttpCodec() {
    }
}
