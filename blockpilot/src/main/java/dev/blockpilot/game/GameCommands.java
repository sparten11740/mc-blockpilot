package dev.blockpilot.game;

import dev.blockpilot.game.log.LogCommands;
import dev.blockpilot.game.observe.ObservationCommands;
import dev.blockpilot.game.perf.PerfCommands;
import dev.blockpilot.protocol.CommandRegistry;

/** Registers every built-in command that needs the game. */
final class GameCommands {
    private GameCommands() {
    }

    static void registerAll(CommandRegistry registry, BlockpilotClient client) {
        new PerceptionCommands(client).register(registry);
        new ActionCommands().register(registry);
        new InputCommands().register(registry);
        new SessionCommands().register(registry);
        new TestWorlds().register(registry);
        new ServerCommands().register(registry);
        new ObservationCommands().register(registry);
        new PerfCommands().register(registry);
        new LogCommands().register(registry);
    }
}
