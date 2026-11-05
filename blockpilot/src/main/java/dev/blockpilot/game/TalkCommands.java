package dev.blockpilot.game;

import static dev.blockpilot.game.PlayerActions.player;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Result;
import dev.blockpilot.protocol.CommandRegistry;

/** Commands that talk to the server as the player. */
final class TalkCommands {
    void register(CommandRegistry registry) {
        registry.register(CommandSpec.action("chat", this::chat).needsWorld().group("talk").arg("text", ArgType.TEXT)
            .help("sends a chat message").example("chat hello"));
        registry.register(CommandSpec.action("cmd", this::command).needsWorld().group("talk")
            .arg("command", ArgType.TEXT)
            .help("sends a /command as the player; feedback arrives as ! chat events (see server for captured output)")
            .example("cmd time set day"));
    }

    private Action chat(CommandContext context, Args args) {
        player().connection.sendChat(args.text("text"));
        return () -> Result.ok();
    }

    private Action command(CommandContext context, Args args) {
        String command = args.text("command");
        player().connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
        return () -> Result.ok();
    }
}
