package dev.blockpilot.game.common;

import dev.blockpilot.api.CommandException;
import dev.blockpilot.mixin.BackupConfirmScreenAccessor;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * The game's question before it loads a world using experimental settings, which a data pack's registry entries (its
 * GameTests, say) make it ask every time, met while Blockpilot opens a world itself. A test world loads without a
 * backup, as the question's skip button does; any other world is a player's to decide on, so the wait fails and the
 * question stays on screen. Other backup questions, such as for a world from another version, aren't touched.
 */
public final class ExperimentalWorlds {
    /** The question's title, the same on 1.21.1 and 26.x. */
    private static final String QUESTION = "selectWorld.backupQuestion.experimental";

    private final boolean testWorld;
    /** The question last answered, so a screen still showing while the world loads isn't answered twice. */
    private Screen answered;
    private boolean experimental;

    /** For the world being opened: whether it is a test world, made by create world. */
    public ExperimentalWorlds(boolean testWorld) {
        this.testWorld = testWorld;
    }

    /** Answers the question if the screen asks it; call on each tick of waiting for the world. */
    public void answer(Screen screen) throws CommandException {
        if (screen == answered || !(screen instanceof BackupConfirmScreen backup)
            || !(screen.getTitle().getContents() instanceof TranslatableContents question)
            || !question.getKey().equals(QUESTION)) {
            return;
        }
        if (!testWorld) {
            // Escape runs the question's cancel, which lets go of the world; close would leave it open.
            throw new CommandException("join_failed", "the game asks whether to back this world up first, as it "
                + "uses experimental settings; only a test world skips it, so the question is on screen, and key "
                + "escape cancels it");
        }
        answered = screen;
        experimental = true;
        ((BackupConfirmScreenAccessor) backup).blockpilot$onProceed().proceed(false, false);
    }

    /** Whether the world was one the game said uses experimental settings. */
    public boolean experimental() {
        return experimental;
    }
}
