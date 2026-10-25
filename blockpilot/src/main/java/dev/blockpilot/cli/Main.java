package dev.blockpilot.cli;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/** The command-line client; it runs on a bare JVM with nothing but the jar, through the launcher the game writes. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
        System.exit(run(args, System.in, out, err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        return run(args, InputStream.nullInputStream(), out, err);
    }

    static int run(String[] args, InputStream in, PrintStream out, PrintStream err) {
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            err.print(Options.USAGE);
            err.println("blockpilot: " + e.getMessage());
            return 2;
        }
        if (options.help()) {
            out.print(Options.USAGE);
            return 0;
        }
        try {
            if (options.mcp()) {
                String version = System.getProperty(Launcher.VERSION_PROPERTY, "unknown");
                BufferedReader lines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                return new StdioServer(lines, out, new GameRelay(options.gameDir(), version, err), version).run();
            }
            return new ShellClient(options, out, err).run();
        } catch (SocketTimeoutException e) {
            err.println("blockpilot: timed out");
            return 2;
        } catch (IOException | IllegalArgumentException e) {
            err.println("blockpilot: " + e.getMessage());
            return 2;
        }
    }
}
