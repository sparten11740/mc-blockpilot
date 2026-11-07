package dev.blockpilot.game.path;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.Duration;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Position;
import dev.blockpilot.api.Result;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Baritone behind the pathfinder port. Blockpilot neither compiles against Baritone (LGPL-3.0) nor ships it: the player
 * installs it, and this adapter reaches its public API by reflection, through the class loader that holds the mods. It
 * is available once that API is loaded and a world gives Baritone a player; Baritone's own settings decide whether its
 * walk breaks or places blocks.
 */
final class BaritonePathfinder implements Pathfinder {
    static final String NAME = "baritone";

    private final Api api = Api.find();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean available() {
        return api != null && Minecraft.getInstance().player != null;
    }

    @Override
    public Action walk(int session, Supplier<Position> target, double near) {
        return new Walk(api, target, near);
    }

    /** Baritone's API, as far as a walk needs it, through reflection. */
    private static final class Api {
        private final Method provider;
        private final Method primary;
        private final Method customGoalProcess;
        private final Method setGoalAndPath;
        private final Method processActive;
        private final Method pathingBehavior;
        private final Method cancelEverything;
        private final Method gameEventHandler;
        private final Method registerEventListener;
        private final Class<?> listenerType;
        private final Constructor<?> goalNear;
        private final Constructor<?> goalBlock;
        /** The Baritone instances the path listener is registered with, so each gets it once. */
        private final Map<Object, Boolean> listening = Collections.synchronizedMap(new WeakHashMap<>());
        /** The walk path events go to; one walk runs at a time, as actions do. */
        private volatile Walk current;

        private Api(ClassLoader loader) throws ReflectiveOperationException {
            Class<?> baritoneApi = Class.forName("baritone.api.BaritoneAPI", true, loader);
            Class<?> providerType = Class.forName("baritone.api.IBaritoneProvider", false, loader);
            Class<?> baritoneType = Class.forName("baritone.api.IBaritone", false, loader);
            Class<?> processType = Class.forName("baritone.api.process.ICustomGoalProcess", false, loader);
            Class<?> behaviorType = Class.forName("baritone.api.behavior.IPathingBehavior", false, loader);
            Class<?> goalType = Class.forName("baritone.api.pathing.goals.Goal", false, loader);
            Class<?> busType = Class.forName("baritone.api.event.listener.IEventBus", false, loader);
            listenerType = Class.forName("baritone.api.event.listener.IGameEventListener", false, loader);
            provider = baritoneApi.getMethod("getProvider");
            primary = providerType.getMethod("getPrimaryBaritone");
            customGoalProcess = baritoneType.getMethod("getCustomGoalProcess");
            setGoalAndPath = processType.getMethod("setGoalAndPath", goalType);
            processActive = processType.getMethod("isActive");
            pathingBehavior = baritoneType.getMethod("getPathingBehavior");
            cancelEverything = behaviorType.getMethod("cancelEverything");
            gameEventHandler = baritoneType.getMethod("getGameEventHandler");
            registerEventListener = busType.getMethod("registerEventListener", listenerType);
            goalNear = Class.forName("baritone.api.pathing.goals.GoalNear", false, loader)
                .getConstructor(BlockPos.class, int.class);
            goalBlock = Class.forName("baritone.api.pathing.goals.GoalBlock", false, loader)
                .getConstructor(BlockPos.class);
        }

        /** The API through the first class loader that has it, or null without Baritone. */
        static Api find() {
            for (ClassLoader loader : new ClassLoader[] {Thread.currentThread().getContextClassLoader(),
                Minecraft.class.getClassLoader(), BaritonePathfinder.class.getClassLoader()}) {
                if (loader == null) {
                    continue;
                }
                try {
                    return new Api(loader);
                } catch (ReflectiveOperationException | LinkageError e) {
                    // Not through this loader; Baritone may be absent altogether.
                }
            }
            return null;
        }

        private Object baritone() throws ReflectiveOperationException {
            Object baritone = primary.invoke(provider.invoke(null));
            if (listening.putIfAbsent(baritone, Boolean.TRUE) == null) {
                Object listener = Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[] {listenerType},
                    (proxy, method, args) -> {
                        if (method.getName().equals("onPathEvent") && args != null && args.length == 1) {
                            Walk walk = current;
                            if (walk != null) {
                                walk.event(((Enum<?>) args[0]).name());
                            }
                            return null;
                        }
                        return switch (method.getName()) {
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            case "toString" -> "Blockpilot's path listener";
                            default -> method.getReturnType() == boolean.class ? Boolean.FALSE : null;
                        };
                    });
                registerEventListener.invoke(gameEventHandler.invoke(baritone), listener);
            }
            return baritone;
        }

        void path(Walk walk, BlockPos goal, int near) throws ReflectiveOperationException {
            current = walk;
            Object target = near < 1 ? goalBlock.newInstance(goal) : goalNear.newInstance(goal, near);
            setGoalAndPath.invoke(customGoalProcess.invoke(baritone()), target);
        }

        boolean active() throws ReflectiveOperationException {
            return (boolean) processActive.invoke(customGoalProcess.invoke(baritone()));
        }

        /** Stops Baritone's goal if this walk set it; one a newer walk set, or the player's own, is left alone. */
        void cancel(Walk walk) {
            if (current != walk) {
                return;
            }
            current = null;
            try {
                cancelEverything.invoke(pathingBehavior.invoke(baritone()));
            } catch (ReflectiveOperationException | RuntimeException e) {
                // The world may be gone; there is nothing left to stop then.
            }
        }
    }

    /** A walk through Baritone, answering as the built-in pathfinder does, with by=baritone. */
    private static final class Walk implements Action {
        /** How far, in blocks, a moving target may get from the goal Baritone walks to before it is set anew. */
        private static final double RETARGET = 2;
        /** Ticks Baritone gets to take the goal on before an idle process counts as giving up. */
        private static final int SETTLING_TICKS = 10;

        private final Api api;
        private final Supplier<Position> target;
        private final double near;
        private BlockPos goal;
        private int ticks;
        private int sinceGoal;
        /** Baritone's latest CALC_FAILED for the goal set last; setting a goal anew clears it. */
        private volatile boolean gaveUp;

        Walk(Api api, Supplier<Position> target, double near) {
            this.api = api;
            this.target = target;
            this.near = near;
        }

        /** Baritone retries after NEXT_CALC_FAILED, walking as near as it can; CALC_FAILED is where it gives up. */
        void event(String name) {
            if (name.equals("CALC_FAILED")) {
                gaveUp = true;
            }
        }

        @Override
        public Result tick() throws CommandException {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                throw new CommandException("not_in_world");
            }
            Position at = target.get();
            if (at == null) {
                api.cancel(this);
                throw failed(Result.error("no_entity", "target gone"));
            }
            ticks++;
            Vec3 point = new Vec3(at.x(), at.y(), at.z());
            BlockPos cell = BlockPos.containing(point);
            boolean arrived = near <= 0 ? player.blockPosition().equals(cell)
                : player.position().distanceTo(point) <= near;
            if (arrived) {
                api.cancel(this);
                return Result.ok(new Fields().bare("took", new Duration(ticks).seconds() + "s").put("by", NAME));
            }
            try {
                if (goal == null || Math.sqrt(goal.distSqr(cell)) > RETARGET) {
                    goal = cell;
                    sinceGoal = 0;
                    gaveUp = false;
                    // Rounded down, so wherever Baritone's goal ends counts as near enough here.
                    api.path(this, cell, (int) Math.floor(near));
                    return null;
                }
                sinceGoal++;
                if (gaveUp) {
                    api.cancel(this);
                    throw failed(Result.error("no_path", "Baritone found none to " + Pathfinders.cell(cell)));
                }
                if (sinceGoal > SETTLING_TICKS && !api.active()) {
                    // Baritone dropped the goal without reaching it, and Blockpilot didn't cancel it.
                    api.cancel(this);
                    throw failed(stuck(player));
                }
            } catch (InvocationTargetException e) {
                api.cancel(this);
                // Baritone itself threw: reported and logged as any other failure inside a command.
                throw new IllegalStateException("Baritone failed", e.getCause());
            } catch (ReflectiveOperationException e) {
                api.cancel(this);
                throw new CommandException("unsupported", "Baritone's API changed: " + e);
            }
            return null;
        }

        private static Result stuck(LocalPlayer player) {
            return Result.error("stuck", Pathfinders.cell(player.blockPosition()));
        }

        /** The error as a CommandException that carries by=baritone. */
        private static CommandException failed(Result error) {
            return new CommandException(error.code(), error.argument(), new Fields().put("by", NAME));
        }

        @Override
        public void cancel() {
            api.cancel(this);
        }

        @Override
        public String label() {
            return "goto";
        }
    }
}
