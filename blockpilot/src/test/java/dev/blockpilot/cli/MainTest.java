package dev.blockpilot.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.blockpilot.wire.Json;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class MainTest {
    private static final String GREETING = "ok blockpilot 0.1.0 api2 mc1.21.1 neoforge\n.\n";

    @TempDir
    Path gameDir;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private final List<String> received = Collections.synchronizedList(new ArrayList<>());
    private ServerSocket server;

    @AfterEach
    void stopGame() throws IOException {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void printsTheReplyAndExitsZero() throws IOException {
        game(line -> "ok status overworld\n  pos 1,2,3\nok inv\n.\n");
        assertEquals(0, run("status\ninv"));
        assertEquals("ok status overworld\n  pos 1,2,3\nok inv", stdout());
        assertEquals("", stderr());
        assertEquals(List.of("auth secret", "status; inv"), received);
    }

    @Test
    void aFailedCommandExitsOneAndIsCounted() throws IOException {
        game(line -> "err unknown_block stoen\nskip\n.\n");
        assertEquals(1, run("mine stoen; jump"));
        assertEquals("blockpilot: 1 err (step 1), 1 skip (nothing after the error ran)", stderr());
    }

    @Test
    void jsonModePrintsOneObject() throws IOException {
        game(line -> line.equals("fmt json") ? "{\"results\":[{\"ok\":true}]}\n"
            : "{\"results\":[{\"ok\":true,\"n\":2}]}\n");
        assertEquals(0, run("--json", "inv"));
        assertEquals("{\"results\":[{\"ok\":true,\"n\":2}]}", stdout());
        assertEquals(List.of("auth secret", "fmt json", "inv"), received);
    }

    @Test
    void aWrongTokenExitsTwo() throws IOException {
        game(line -> "ok\n.\n");
        Files.writeString(gameDir.resolve("token"), "other\n");
        assertEquals(2, run("status"));
        assertEquals("err auth", stderr());
    }

    @Test
    void waitsUntilThePlayerIsInAWorld() throws IOException {
        int[] statuses = {0};
        game(line -> !line.equals("status") ? "ok jump\n.\n"
            : statuses[0]++ == 0 ? "ok menu title\n.\n" : "ok overworld\n.\n");
        assertEquals(0, run("--wait-ready", "30", "--wait-world", "jump"));
        assertEquals(List.of("auth secret", "status", "status", "jump"), received);
    }

    @Test
    void aWorldThatNeverComesTimesOutSayingHowLong() throws IOException {
        game(line -> "ok menu title\n.\n");
        assertEquals(2, run("--wait-ready", "0.5", "--wait-world", "jump"));
        assertEquals("blockpilot: timed out after 0.5s waiting for a world (last status: ok menu title)", stderr());
    }

    @Test
    void aReplyThatNeverComesTimesOutNamingTheLimit() throws IOException {
        game(line -> "");
        assertEquals(2, run("--timeout", "0.5", "wait 600s"));
        assertEquals("blockpilot: no reply within 0.5s, so the script was cancelled; a longer script needs a larger "
            + "--timeout", stderr());
    }

    @Test
    void aStaleEndpointFileIsUnreachable() throws Exception {
        Process exited = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-version").redirectErrorStream(true).start();
        exited.getInputStream().transferTo(new ByteArrayOutputStream());
        exited.waitFor();
        game(line -> "ok\n.\n");
        writeEndpointFile(exited.pid());
        assertEquals(2, run("status"));
        assertEquals("blockpilot: game not reachable: endpoint.json is stale (game process " + exited.pid()
            + " is not running)", stderr());
    }

    @Test
    void noGameIsUnreachable() {
        assertEquals(2, run("status"));
        assertTrue(stderr().startsWith("blockpilot: game not reachable: no "), stderr());
    }

    @Test
    void aCrashReportWrittenWhileWaitingEndsTheWait() throws Exception {
        Path report = gameDir.resolve("crash-reports/crash-2026-10-25_23.00.00-client.txt");
        Thread crash = new Thread(() -> {
            try {
                Thread.sleep(1500);
                Files.createDirectories(report.getParent());
                Files.writeString(report, "---- Minecraft Crash Report ----\n");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        crash.start();
        long started = System.nanoTime();
        assertEquals(2, run("--wait-ready", "30", "status"));
        crash.join();
        assertTrue(System.nanoTime() - started < 10_000_000_000L, "waited past the crash");
        assertEquals("blockpilot: the game crashed while starting: " + report, stderr());
    }

    @Test
    void anOlderCrashReportDoesNotEndTheWait() throws IOException {
        Path report = Files.createDirectories(gameDir.resolve("crash-reports")).resolve("crash-old-client.txt");
        Files.writeString(report, "---- Minecraft Crash Report ----\n");
        Files.setLastModifiedTime(report, FileTime.fromMillis(System.currentTimeMillis() - 3_600_000));
        assertEquals(2, run("--wait-ready", "2", "status"));
        assertTrue(stderr().startsWith("blockpilot: game not reachable after 2s: "), stderr());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void quitReturnsOnceTheGameHasExited() throws IOException {
        Process game = new ProcessBuilder("sleep", "1").start();
        game(line -> "ok pid=" + game.pid() + "\n.\n");
        assertEquals(0, run("quit"));
        assertFalse(game.isAlive());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void quitFailsWhileTheGameKeepsRunning() throws IOException {
        Process game = new ProcessBuilder("sleep", "30").start();
        try {
            game(line -> "ok pid=" + game.pid() + "\n.\n");
            assertEquals(2, run("--timeout", "0.5", "quit"));
            assertEquals("blockpilot: the game (pid " + game.pid() + ") is still running after 0.5s", stderr());
        } finally {
            game.destroy();
        }
    }

    @Test
    void usageErrorsExitTwoInOneLineAndHelpExitsZero() {
        assertEquals(2, run());
        assertEquals("blockpilot: a script is required; see --help", stderr());
        assertEquals("", stdout());
        assertEquals(0, Main.run(new String[] {"--help"}, new PrintStream(out), new PrintStream(err)));
        assertTrue(stdout().startsWith("usage: blockpilot"));
    }

    @Test
    void aScriptInSeveralArgumentsSaysToPutItInOne() {
        assertEquals(2, run("status", "inv"));
        assertEquals("blockpilot: only one script: put its commands in one argument, separated by semicolons; see "
            + "--help", stderr());
    }

    /** A line-protocol endpoint for one connection: it checks the token, then answers each line with the reply. */
    private void game(UnaryOperator<String> replies) throws IOException {
        server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        Thread thread = new Thread(() -> {
            try (Socket socket = server.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                    StandardCharsets.UTF_8));
                Writer reply = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)) {
                String line;
                while ((line = in.readLine()) != null) {
                    received.add(line);
                    reply.write(line.startsWith("auth ") ? line.equals("auth secret") ? GREETING : "err auth\n.\n"
                        : replies.apply(line));
                    reply.flush();
                }
            } catch (IOException e) {
                // The test closed the server.
            }
        });
        thread.setDaemon(true);
        thread.start();
        Files.writeString(gameDir.resolve("token"), "secret\n");
        writeEndpointFile(ProcessHandle.current().pid());
    }

    private void writeEndpointFile(long pid) throws IOException {
        Files.createDirectories(gameDir.resolve("blockpilot"));
        Files.writeString(gameDir.resolve("blockpilot/endpoint.json"), Json.write(Map.of("port", server.getLocalPort(),
            "pid", pid, "token_file", gameDir.resolve("token").toString())));
    }

    private int run(String... args) {
        String[] withGameDir = new String[args.length + 2];
        withGameDir[0] = "--game-dir";
        withGameDir[1] = gameDir.toString();
        System.arraycopy(args, 0, withGameDir, 2, args.length);
        return Main.run(withGameDir, new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private String stdout() {
        return out.toString(StandardCharsets.UTF_8).strip();
    }

    private String stderr() {
        return err.toString(StandardCharsets.UTF_8).strip();
    }
}
