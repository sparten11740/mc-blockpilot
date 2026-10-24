package dev.blockpilot.game;

import dev.blockpilot.protocol.CommandRegistry;

/** Registers every built-in command that needs the game. */
final class GameCommands {
    private GameCommands() {
    }

    static void registerAll(CommandRegistry registry, BlockpilotClient client) {
        new PerceptionCommands(client).register(registry);
        new ActionCommands().register(registry);
        new InputCommands().register(registry);
    }
}
