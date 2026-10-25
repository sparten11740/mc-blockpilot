package dev.blockpilot.cli;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** One script over the line protocol: connect (waiting if asked), authenticate, send, print, and pick the exit code. */
final class ShellClient {
    private final Options options;
    private final PrintStream out;
    private final PrintStream err;
    private Socket socket;
    private BufferedReader reader;
    private Writer writer;

    ShellClient(Options options, PrintStream out, PrintStream err) {
        this.options = options;
        this.out = out;
        this.err = err;
    }

    int run() throws IOException {
        // Capped so the sum cannot overflow; nanoTime values are only compared by their difference.
        long deadline = System.nanoTime() + (long) Math.min(options.waitReady() * 1e9, Long.MAX_VALUE / 2);
        String token;
        try {
            token = connect(deadline);
        } catch (Crashed e) {
            err.println("blockpilot: the game crashed while starting: " + e.getMessage());
            return 2;
        } catch (IOException e) {
            err.println("blockpilot: game not reachable: " + e.getMessage());
            return 2;
        }
        String reply;
        try (Socket connection = socket) {
            send("auth " + token);
            String greeting = textReply();
            if (!greeting.startsWith("ok")) {
                err.println(greeting);
                return 2;
            }
            if (options.waitWorld() && !waitForWorld(deadline)) {
                return 2;
            }
            if (options.json()) {
                send("fmt json");
                readLine();
            }
            send(options.script().replace("\n", "; "));
            reply = options.json() ? readLine() : textReply();
        }
        out.println(reply);
        List<Results.Result> results = Results.of(reply);
        String summary = Results.summary(results);
        if (summary != null) {
            err.println(summary);
        }
        Long pid = Results.quitPid(reply);
        if (pid != null && !EndpointFile.waitExit(pid, options.timeout())) {
            err.println("blockpilot: the game (pid " + pid + ") is still running after "
                + BigDecimal.valueOf(options.timeout()).stripTrailingZeros().toPlainString() + "s");
            return 2;
        }
        return results.stream().anyMatch(Results.Result::isErr) ? 1 : 0;
    }

    private String connect(long deadline) throws IOException {
        int timeoutMillis = (int) Math.min(Integer.MAX_VALUE, options.timeout() * 1000);
        // Whole seconds, as some file systems keep modification times.
        long waitStarted = System.currentTimeMillis() / 1000 * 1000;
        while (true) {
            Socket candidate = new Socket();
            try {
                EndpointFile endpoint = EndpointFile.read(options.gameDir());
                candidate.connect(new InetSocketAddress("127.0.0.1", endpoint.port()), timeoutMillis);
                candidate.setSoTimeout(timeoutMillis);
                socket = candidate;
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                return endpoint.token();
            } catch (IOException e) {
                candidate.close();
                Path crash = crashReportSince(waitStarted);
                if (crash != null) {
                    throw new Crashed(crash);
                }
                if (System.nanoTime() - deadline >= 0) {
                    throw e;
                }
                pause();
            }
        }
    }

    /** A game that crashes while starting never writes endpoint.json, but Minecraft writes a crash report. */
    private Path crashReportSince(long since) throws IOException {
        Path folder = options.gameDir().resolve("crash-reports");
        if (!Files.isDirectory(folder)) {
            return null;
        }
        try (Stream<Path> reports = Files.list(folder)) {
            return reports.filter(report -> report.getFileName().toString().endsWith(".txt"))
                .filter(report -> modified(report) >= since)
                .max(Comparator.comparingLong(ShellClient::modified)).orElse(null);
        }
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private static final class Crashed extends IOException {
        Crashed(Path report) {
            super(report.toString());
        }
    }

    private boolean waitForWorld(long deadline) throws IOException {
        while (true) {
            send("status");
            String status = textReply();
            if (!status.startsWith("ok menu")) {
                return true;
            }
            if (System.nanoTime() - deadline >= 0) {
                err.println("blockpilot: timed out waiting for a world (last status: " + status.lines().findFirst()
                    .orElse("") + ")");
                return false;
            }
            pause();
        }
    }

    private void send(String line) throws IOException {
        writer.write(line + "\n");
        writer.flush();
    }

    /** Lines up to the {@code .} terminator; a script that switches to {@code fmt json} gets one JSON line instead. */
    private String textReply() throws IOException {
        List<String> lines = new ArrayList<>();
        while (true) {
            String line = readLine();
            if (line.equals(".")) {
                return String.join("\n", lines);
            }
            if (lines.isEmpty() && line.startsWith("{")) {
                return line;
            }
            lines.add(line);
        }
    }

    private String readLine() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new IOException("connection closed");
        }
        return line;
    }

    private static void pause() throws IOException {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }
    }
}
