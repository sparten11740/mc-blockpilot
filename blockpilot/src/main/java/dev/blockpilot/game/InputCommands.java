package dev.blockpilot.game;

import com.mojang.blaze3d.platform.InputConstants;
import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Duration;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Point;
import dev.blockpilot.api.Result;
import dev.blockpilot.game.input.Injection;
import dev.blockpilot.game.input.InputState;
import dev.blockpilot.mixin.KeyMappingAccessor;
import dev.blockpilot.protocol.CommandRegistry;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ClickType;

/**
 * Raw input: mouse buttons, movement and drags, scrolling, keys and typed text, fed through the game's own input
 * handlers so any interaction a human does with clicks, holds and drags works, including modded ones. On screens,
 * coordinates are GUI-scaled, as {@code screen} reports them.
 */
final class InputCommands {
    private static final String[] BUTTONS = {"left", "right", "middle"};

    void register(CommandRegistry registry) {
        registry.register(CommandSpec.query("screen", (context, args) -> Screens.describe()).diffable()
            .group("gui").help("the open GUI: type, title, size, cursor, container slots s<i>, widgets w<i> "
                + "with @x,y,w,h bounds (GUI-scaled)").example("screen"));
        registry.register(CommandSpec.action("mouse", this::mouse).group("input")
            .form(form -> form.keyword("op", "down", "up").keyword("button", BUTTONS))
            .form(form -> form.keyword("op", "click").keyword("button", BUTTONS).optionalArg("ticks", ArgType.DURATION))
            .form(form -> form.keyword("op", "move").arg("dx", ArgType.NUMBER).arg("dy", ArgType.NUMBER)
                .optionalArg("ticks", ArgType.DURATION))
            .help("down|up holds or lets go of a button (held buttons stay held); click presses it for ticks "
                + "(default 1t); move turns the camera by raw pixels in-world, or moves the cursor by GUI units on a "
                + "screen, spread over ticks").example("mouse down right"));
        registry.register(CommandSpec.action("drag", this::drag).group("input")
            .form(form -> form.keyword("button", BUTTONS).arg("dx", ArgType.NUMBER).arg("dy", ArgType.NUMBER)
                .optionalArg("ticks", ArgType.DURATION))
            .form(form -> form.keyword("button", BUTTONS).arg("from", ArgType.POINT, ArgType.WIDGET)
                .arg("to", ArgType.POINT, ArgType.WIDGET).optionalArg("ticks", ArgType.DURATION))
            .help("holds the button, moves and lets go (default over 10t): in-world by <dx> <dy> pixels, on a screen "
                + "from one GUI point or widget to another").example("drag right 0 -120 30t"));
        registry.register(CommandSpec.action("cursor", this::cursor).group("gui")
            .arg("at", ArgType.POINT, ArgType.WIDGET)
            .help("moves the cursor to GUI coordinates x,y (or widget w<i>'s centre) on the open screen")
            .example("cursor 120,80"));
        registry.register(CommandSpec.action("click", this::click).group("gui")
            .form(form -> form.arg("at", ArgType.POINT, ArgType.WIDGET).optionalKeyword("button", BUTTONS))
            .form(form -> form.arg("slot", ArgType.MENU_SLOT)
                .optionalKeyword("mode", "left", "right", "shift", "0", "1", "2", "3", "4", "5", "6", "7", "8"))
            .help("clicks a GUI point or a widget's centre (w<index>, as screen lists it), or a container slot "
                + "s<index>: left, right, shift (quick move) or 0-8 (swap with that hotbar slot)").example("click w0"));
        registry.register(CommandSpec.action("scroll", this::scroll).group("input").arg("amount", ArgType.NUMBER)
            .optionalArg("at", ArgType.POINT, ArgType.WIDGET)
            .help("scrolls by n notches (positive up), optionally at x,y or w<i>").example("scroll -2"));
        registry.register(CommandSpec.action("key", this::key).group("input")
            .keyword("op", "down", "up", "press").arg("key", ArgType.WORD).optionalArg("ticks", ArgType.DURATION)
            .help("a key by name (w, space, left_shift, e, f5, escape, enter) or key mapping id (key.use, "
                + "key.inventory, a mod's mapping); press = down then up after Nt (default 1t)")
            .example("key press key.inventory"));
        registry.register(CommandSpec.action("type", this::type).group("input").arg("text", ArgType.TEXT)
            .help("types text into the focused text field").example("type hello"));
        registry.register(CommandSpec.action("release", (context, args) -> {
            InputState.releaseAll();
            return () -> Result.ok();
        }).group("input").help("lets go of every held key, button and movement").example("release"));
        registry.register(CommandSpec.action("close", (context, args) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen == null) {
                throw new CommandException("no_screen");
            }
            if (minecraft.player != null && minecraft.player.containerMenu != minecraft.player.inventoryMenu) {
                minecraft.player.closeContainer();
            } else {
                minecraft.setScreen(null);
            }
            return () -> Result.ok();
        }).group("gui").help("closes the open screen or container").example("close"));
    }

    private static Minecraft minecraft() {
        return Minecraft.getInstance();
    }

    /** One of {@link #BUTTONS}, as GLFW numbers them. */
    private static int button(String name) {
        return List.of(BUTTONS).indexOf(name);
    }

    /** A {@code x,y} point or widget {@code w<i>}'s centre, in GUI units, on the open screen. */
    private static double[] guiPoint(Args args, String name) throws CommandException {
        requireScreen();
        if (args.type(name) == ArgType.WIDGET) {
            return Screens.widgetCentre(minecraft().screen, args.index(name, 0));
        }
        Point point = args.point(name);
        return new double[] {point.x(), point.y()};
    }

    /** GUI-scaled coordinates to the window's screen pixels, which the mouse handler works in. */
    private static double[] toScreen(double guiX, double guiY) {
        var window = minecraft().getWindow();
        return new double[] {guiX * window.getScreenWidth() / window.getGuiScaledWidth(),
            guiY * window.getScreenHeight() / window.getGuiScaledHeight()};
    }

    private static double guiScale() {
        var window = minecraft().getWindow();
        return window.getScreenWidth() / (double) window.getGuiScaledWidth();
    }

    private static int ticks(Args args, int fallback) {
        Duration duration = args.duration("ticks");
        return duration == null ? fallback : Math.max(1, duration.ticks());
    }

    private Action mouse(CommandContext context, Args args) throws CommandException {
        int session = context.sessionId();
        return switch (args.word("op")) {
            case "down" -> {
                InputState.holdButton(session, button(args.word("button")));
                yield () -> Result.ok();
            }
            case "up" -> {
                InputState.releaseButton(button(args.word("button")));
                yield () -> Result.ok();
            }
            case "click" -> holdFor(session, button(args.word("button")), ticks(args, 1));
            default -> move(args.number("dx", 0), args.number("dy", 0), ticks(args, 1));
        };
    }

    private static Action holdFor(int session, int button, int ticks) {
        int[] step = {0};
        return new Action() {
            @Override
            public Result tick() {
                if (step[0]++ == 0) {
                    InputState.holdButton(session, button);
                }
                if (step[0] < ticks) {
                    return null;
                }
                InputState.releaseButton(button);
                return Result.ok();
            }

            @Override
            public void cancel() {
                InputState.releaseButton(button);
            }

            @Override
            public String label() {
                return "mouse";
            }
        };
    }

    /** Relative movement spread over ticks; in-world raw pixels, on screens GUI units. */
    private static Action move(double dx, double dy, int ticks) {
        double scale = minecraft().screen != null ? guiScale() : 1;
        int[] step = {0};
        return ActionCommands.named("mouse", () -> {
            var mouse = minecraft().mouseHandler;
            Injection.mouseMoveTo(mouse.xpos() + dx * scale / ticks, mouse.ypos() + dy * scale / ticks);
            return ++step[0] >= ticks ? Result.ok() : null;
        });
    }

    private Action drag(CommandContext context, Args args) throws CommandException {
        int button = button(args.word("button"));
        int ticks = ticks(args, 10);
        int session = context.sessionId();
        if (args.has("from")) {
            double[] fromGui = guiPoint(args, "from");
            double[] toGui = guiPoint(args, "to");
            double[] from = toScreen(fromGui[0], fromGui[1]);
            double[] to = toScreen(toGui[0], toGui[1]);
            int[] step = {0};
            return dragAction(session, button, () -> {
                int current = step[0]++;
                if (current == 0) {
                    Injection.mouseMoveTo(from[0], from[1]);
                    return false;
                }
                double progress = Math.min(1, current / (double) ticks);
                Injection.mouseMoveTo(from[0] + (to[0] - from[0]) * progress, from[1] + (to[1] - from[1]) * progress);
                return progress >= 1;
            });
        }
        if (minecraft().screen != null) {
            throw new CommandException("syntax", "on a screen, drag <button> <x1,y1|w<i>> <x2,y2|w<i>> [ticks]");
        }
        double dx = args.number("dx", 0);
        double dy = args.number("dy", 0);
        int[] step = {0};
        return dragAction(session, button, () -> {
            if (step[0]++ == 0) {
                return false;
            }
            var mouse = minecraft().mouseHandler;
            Injection.mouseMoveTo(mouse.xpos() + dx / ticks, mouse.ypos() + dy / ticks);
            return step[0] > ticks;
        });
    }

    /** Presses the button after the first movement step, moves until the step reports done, then releases. */
    private static Action dragAction(int session, int button, java.util.function.BooleanSupplier stepMovement) {
        return new Action() {
            private boolean pressed;
            private boolean released;

            @Override
            public Result tick() {
                if (released) {
                    return Result.ok();
                }
                boolean done = stepMovement.getAsBoolean();
                if (!pressed) {
                    InputState.holdButton(session, button);
                    pressed = true;
                    return null;
                }
                if (done) {
                    InputState.releaseButton(button);
                    released = true;
                }
                return null;
            }

            @Override
            public void cancel() {
                InputState.releaseButton(button);
            }

            @Override
            public String label() {
                return "drag";
            }
        };
    }

    private Action cursor(CommandContext context, Args args) throws CommandException {
        double[] point = guiPoint(args, "at");
        double[] screen = toScreen(point[0], point[1]);
        Injection.mouseMoveTo(screen[0], screen[1]);
        return () -> Result.ok();
    }

    private static void requireScreen() throws CommandException {
        if (minecraft().screen == null) {
            throw new CommandException("no_screen");
        }
    }

    private Action click(CommandContext context, Args args) throws CommandException {
        requireScreen();
        if (args.has("slot")) {
            var player = minecraft().player;
            if (player == null) {
                throw new CommandException("not_in_world");
            }
            int slot = args.index("slot", 0);
            String modifier = args.has("mode") ? args.word("mode") : "left";
            var menu = player.containerMenu;
            if (slot >= menu.slots.size()) {
                throw new CommandException("syntax", "the menu has slots s0-s" + (menu.slots.size() - 1));
            }
            int button;
            ClickType type;
            switch (modifier) {
                case "left" -> {
                    button = 0;
                    type = ClickType.PICKUP;
                }
                case "right" -> {
                    button = 1;
                    type = ClickType.PICKUP;
                }
                case "shift" -> {
                    button = 0;
                    type = ClickType.QUICK_MOVE;
                }
                default -> {
                    button = Integer.parseInt(modifier);
                    type = ClickType.SWAP;
                }
            }
            minecraft().gameMode.handleInventoryMouseClick(menu.containerId, slot, button, type, player);
            return () -> Result.ok();
        }
        double[] point = guiPoint(args, "at");
        double[] screen = toScreen(point[0], point[1]);
        Injection.mouseMoveTo(screen[0], screen[1]);
        return holdFor(context.sessionId(), button(args.has("button") ? args.word("button") : "left"), 1);
    }

    private Action scroll(CommandContext context, Args args) throws CommandException {
        if (args.has("at")) {
            double[] point = guiPoint(args, "at");
            double[] screen = toScreen(point[0], point[1]);
            Injection.mouseMoveTo(screen[0], screen[1]);
        }
        Injection.scroll(args.number("amount", 1));
        return () -> Result.ok();
    }

    /** A key token: a key mapping id resolves to its bound key; otherwise a keyboard or mouse key name. */
    static InputConstants.Key resolveKey(String name) throws CommandException {
        for (KeyMapping mapping : minecraft().options.keyMappings) {
            if (mapping.getName().equals(name)) {
                InputConstants.Key key = ((KeyMappingAccessor) mapping).blockpilot$key();
                if (key.equals(InputConstants.UNKNOWN)) {
                    throw new CommandException("syntax", name + " is not bound to a key");
                }
                return key;
            }
        }
        for (String prefix : new String[] {"key.keyboard.", "key.mouse.", ""}) {
            try {
                InputConstants.Key key = InputConstants.getKey(prefix + name);
                if (!key.equals(InputConstants.UNKNOWN)) {
                    return key;
                }
            } catch (IllegalArgumentException e) {
                // Try the next prefix.
            }
        }
        throw new CommandException("syntax", "unknown key " + name + "; use names like w, space, left_shift, f5 "
            + "or a mapping id like key.use");
    }

    private Action key(CommandContext context, Args args) throws CommandException {
        InputConstants.Key key = resolveKey(args.word("key"));
        if (key.equals(((KeyMappingAccessor) BlockpilotClient.get().toggleKey()).blockpilot$key())) {
            throw new CommandException("syntax", "the agent control toggle is the player's; agents can't press it");
        }
        boolean mouse = key.getType() == InputConstants.Type.MOUSE;
        int code = key.getValue();
        int session = context.sessionId();
        String op = args.word("op");
        if (op.equals("down")) {
            if (mouse) {
                InputState.holdButton(session, code);
            } else {
                InputState.holdKey(session, code);
            }
            return () -> Result.ok();
        }
        if (op.equals("up")) {
            if (mouse) {
                InputState.releaseButton(code);
            } else {
                InputState.releaseKey(code);
            }
            return () -> Result.ok();
        }
        if (mouse) {
            return holdFor(session, code, ticks(args, 1));
        }
        int ticks = ticks(args, 1);
        int[] step = {0};
        return new Action() {
            @Override
            public Result tick() {
                if (step[0]++ == 0) {
                    InputState.holdKey(session, code);
                }
                if (step[0] < ticks) {
                    return null;
                }
                InputState.releaseKey(code);
                return Result.ok();
            }

            @Override
            public void cancel() {
                InputState.releaseKey(code);
            }

            @Override
            public String label() {
                return "key";
            }
        };
    }

    private Action type(CommandContext context, Args args) {
        args.text("text").codePoints().forEach(Injection::typeCharacter);
        return () -> Result.ok(new Fields().put("typed", args.text("text").length(),
            "typed " + args.text("text").length()));
    }
}
