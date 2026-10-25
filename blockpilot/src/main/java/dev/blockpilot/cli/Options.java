package dev.blockpilot.cli;

import java.nio.file.Path;

/** The command line. Options may come in any order and a later one wins, so the launcher's preset can be overridden. */
record Options(Path gameDir, boolean json, double timeout, double waitReady, boolean waitWorld, boolean help,
    String script) {
    static final String USAGE = """
        usage: blockpilot [options] "<script>"

        Sends one Blockpilot script to the running game and prints the reply.

          --game-dir <dir>        the game's directory (default: the launcher's, else run)
          --json                  print the reply as one JSON object
          --timeout <seconds>     wait this long for the reply, and after quit for the game to exit (default 300)
          --wait-ready <seconds>  wait this long for the game to come up (default 0)
          --wait-world            also wait, within --wait-ready, until the player is in a world

        Exit code: 0 if every command returned ok, 1 if any returned err, 2 if the game could not be reached.
        """;

    static Options parse(String... args) {
        Path gameDir = Path.of("run");
        boolean json = false;
        double timeout = 300;
        double waitReady = 0;
        boolean waitWorld = false;
        boolean help = false;
        String script = null;
        boolean optionsEnded = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (optionsEnded || !arg.startsWith("-") || arg.equals("-")) {
                if (script != null) {
                    throw new IllegalArgumentException("only one script; separate its commands with ;");
                }
                script = arg;
                continue;
            }
            int equals = arg.indexOf('=');
            String name = equals > 0 ? arg.substring(0, equals) : arg;
            String inline = equals > 0 ? arg.substring(equals + 1) : null;
            switch (name) {
                case "--" -> optionsEnded = true;
                case "--json", "--wait-world", "--help", "-h" -> {
                    if (inline != null) {
                        throw new IllegalArgumentException(name + " takes no value");
                    }
                    json |= name.equals("--json");
                    waitWorld |= name.equals("--wait-world");
                    help |= name.equals("--help") || name.equals("-h");
                }
                case "--game-dir", "--timeout", "--wait-ready" -> {
                    String value = inline;
                    if (value == null) {
                        if (++i == args.length) {
                            throw new IllegalArgumentException(name + " needs a value");
                        }
                        value = args[i];
                    }
                    switch (name) {
                        case "--game-dir" -> gameDir = Path.of(value);
                        case "--timeout" -> timeout = seconds(name, value);
                        default -> waitReady = seconds(name, value);
                    }
                }
                default -> throw new IllegalArgumentException("unknown option " + name);
            }
        }
        if (script == null && !help) {
            throw new IllegalArgumentException("a script is required");
        }
        return new Options(gameDir, json, timeout, waitReady, waitWorld, help, script);
    }

    private static double seconds(String option, String value) {
        try {
            double seconds = Double.parseDouble(value);
            if (seconds >= 0 && Double.isFinite(seconds)) {
                return seconds;
            }
        } catch (NumberFormatException e) {
            // Reported below like a negative number.
        }
        throw new IllegalArgumentException(option + " takes a number of seconds, not " + value);
    }
}
