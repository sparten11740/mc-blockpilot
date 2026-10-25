package dev.blockpilot.game;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.JsonOps;
import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Duration;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Numbers;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import dev.blockpilot.protocol.CommandRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * The integrated server in singleplayer, reached in the same process: server commands with their result value and
 * feedback captured, server-side NBT, and tick control. All of it runs on the server thread and only in worlds that
 * allow commands.
 */
final class ServerCommands {
    private static final int MAX_NBT_CHARS = 8192;
    private static boolean changedTicking;

    void register(CommandRegistry registry) {
        registry.register(CommandSpec.action("server", this::server).onServer().group("integrated")
            .arg("command", ArgType.TEXT).option("as", "player: run as the player, at the player's position",
                List.of("player"))
            .option("settle", "server ticks to wait before answering so the client has the changes (default 2t; "
                + "0t for pure probes)", ArgType.DURATION, ArgType.SECONDS)
            .help("runs a command on the integrated server (op level) and returns its result value, success and "
                + "every feedback line; options go before the command (server as=player settle=0t <command>)")
            .field("result", "the command's return value").field("success", "true/false")
            .example("server as=player setblock ~ ~-1 ~ minecraft:stone"));
        registry.register(CommandSpec.query("nbt", this::nbt).onServer().group("integrated")
            .arg("target", ArgType.POSITION, ArgType.ENTITY, ArgType.UUID)
            .option("path", "NBT path inside the data, e.g. Pos or Items[0].id")
            .help("server-side data of a block entity (<pos>) or entity (e<id> or uuid); SNBT in text, JSON in json")
            .field("value", "the data").example("nbt e812 path=Pos"));
        registry.register(CommandSpec.action("tick", this::tick).onServer().group("integrated")
            .form(form -> form.keyword("op", "freeze", "unfreeze", "query"))
            .form(form -> form.keyword("op", "step").arg("ticks", ArgType.INT))
            .form(form -> form.keyword("op", "rate").arg("tps", ArgType.NUMBER))
            .help("tick freeze|unfreeze|query, tick rate <tps>, tick step <n> (returns after n ticks ran); "
                + "'tick' is the overworld game time").field("tick", "game time").field("frozen", "true/false")
            .field("rate", "target ticks per second").example("tick step 20"));
    }

    static MinecraftServer server() {
        return Minecraft.getInstance().getSingleplayerServer();
    }

    static ServerPlayer serverPlayer() {
        var player = Minecraft.getInstance().player;
        return player == null ? null : server().getPlayerList().getPlayer(player.getUUID());
    }

    static ServerLevel serverLevel() {
        ServerPlayer player = serverPlayer();
        return player != null ? player.serverLevel() : server().overworld();
    }

    /** Collects what a command tells its source. */
    private static final class Capture implements CommandSource {
        final List<String> lines = new ArrayList<>();
        /** The last line that is not Brigadier's "...command<--[HERE]" pointer: the error message, on failure. */
        String error;

        @Override
        public void sendSystemMessage(Component message) {
            lines.add(message.getString());
            if (!mentions(message, "command.context.here")) {
                error = message.getString();
            }
        }

        /** Whether the component or any part nested in it is the translation {@code key}. */
        private static boolean mentions(Component component, String key) {
            if (component.getContents() instanceof TranslatableContents contents && contents.getKey().equals(key)) {
                return true;
            }
            return component.getSiblings().stream().anyMatch(part -> mentions(part, key));
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }

    private Action server(CommandContext context, Args args) throws CommandException {
        MinecraftServer server = server();
        String command = args.text("command");
        Capture capture = new Capture();
        boolean[] success = {false};
        int[] value = {0};
        CommandSourceStack source;
        if ("player".equals(args.option("as"))) {
            ServerPlayer player = serverPlayer();
            if (player == null) {
                throw new CommandException("not_in_world");
            }
            source = player.createCommandSourceStack().withSource(capture).withPermission(4);
        } else {
            ServerLevel level = server.overworld();
            source = new CommandSourceStack(capture, Vec3.atLowerCornerOf(level.getSharedSpawnPos()), Vec2.ZERO, level, 4,
                "Blockpilot", Component.literal("Blockpilot"), server, null);
        }
        source = source.withCallback((ok, result) -> {
            success[0] = ok;
            value[0] = result;
        });
        server.getCommands().performPrefixedCommand(source, command);
        List<Row> rows = new ArrayList<>();
        for (int index = 0; index < capture.lines.size(); index++) {
            rows.add(Row.of(String.valueOf(index), new Fields().bare("line", capture.lines.get(index))));
        }
        Result result = success[0]
            ? Result.ok(new Fields().put("result", value[0], "result=" + value[0]).put("success", true, "success=true"))
            : Result.error("command_failed", capture.error == null ? command : capture.error)
                .with(new Fields().put("result", value[0], "result=" + value[0]));
        Result answer = result.withRows(rows);
        // The server sends block and entity changes to the client during its next tick; answer only once the
        // client can see them, so a following client query (block, look, status) reads the new state.
        Duration settle = args.durationOption("settle");
        int[] remaining = {settle == null ? 2 : settle.ticks()};
        return () -> remaining[0]-- <= 0 ? answer : null;
    }

    private Result nbt(CommandContext context, Args args) throws CommandException {
        String token = args.word("target");
        ServerLevel level = serverLevel();
        CompoundTag data;
        if (args.type("target") != ArgType.POSITION) {
            Entity entity = args.type("target") == ArgType.ENTITY ? level.getEntity(args.entity("target").id())
                : findEntity(UUID.fromString(token));
            if (entity == null) {
                throw new CommandException("no_entity", token);
            }
            data = entity.saveWithoutId(new CompoundTag());
        } else {
            BlockPos pos = Rel.block(context.resolve(args.position("target")));
            if (!level.isLoaded(pos)) {
                throw new CommandException("unloaded", token);
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null) {
                throw new CommandException("no_block", "no block entity at " + token);
            }
            data = blockEntity.saveWithFullMetadata(level.registryAccess());
        }
        Tag value = data;
        String path = args.option("path");
        if (path != null) {
            try {
                List<Tag> found = NbtPathArgument.nbtPath().parse(new StringReader(path)).get(data);
                value = found.get(0);
            } catch (CommandSyntaxException e) {
                throw new CommandException("syntax", "path " + path + ": " + e.getMessage());
            }
        }
        String text = value.toString();
        Fields fields = new Fields();
        if (text.length() > MAX_NBT_CHARS) {
            fields.put("value", text.substring(0, MAX_NBT_CHARS), text.substring(0, MAX_NBT_CHARS))
                .put("truncated", text.length(), "truncated " + text.length() + " (narrow with path=)");
        } else {
            fields.put("value", NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, value), text);
        }
        return Result.ok(fields);
    }

    private static UUID uuid(String token) {
        try {
            return token.length() == 36 ? UUID.fromString(token) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Entity findEntity(UUID uuid) {
        for (ServerLevel level : server().getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private static long gameTime() {
        return server().overworld().getGameTime();
    }

    private static Fields tickState() {
        ServerTickRateManager ticks = server().tickRateManager();
        return new Fields().put("tick", gameTime(), "tick=" + gameTime())
            .put("frozen", ticks.isFrozen(), "frozen=" + ticks.isFrozen())
            .put("rate", (double) ticks.tickrate(), "rate=" + Numbers.format(ticks.tickrate()));
    }

    private Action tick(CommandContext context, Args args) throws CommandException {
        ServerTickRateManager ticks = server().tickRateManager();
        switch (args.word("op")) {
            case "freeze" -> {
                ticks.setFrozen(true);
                changedTicking = true;
            }
            case "unfreeze" -> ticks.setFrozen(false);
            case "rate" -> {
                double rate = args.number("tps", 20);
                if (rate < 1 || rate > 10000) {
                    throw new CommandException("syntax", "tick rate 1-10000");
                }
                ticks.setTickRate((float) rate);
                changedTicking = true;
            }
            case "step" -> {
                if (!ticks.isFrozen()) {
                    throw new CommandException("not_frozen", "tick freeze first");
                }
                int steps = args.integer("ticks", 1);
                long target = gameTime() + steps;
                ticks.stepGameIfPaused(steps);
                return new Action() {
                    @Override
                    public Result tick() {
                        return gameTime() >= target && !ticks.isSteppingForward() ? Result.ok(tickState()) : null;
                    }

                    @Override
                    public String label() {
                        return "tick step";
                    }
                };
            }
            default -> {
            }
        }
        return () -> Result.ok(tickState());
    }

    /** Undoes freezing and rate changes Blockpilot made, so a world is never saved frozen because of it. */
    static void onServerStopping(MinecraftServer server) {
        if (changedTicking) {
            server.tickRateManager().setFrozen(false);
            server.tickRateManager().setTickRate(20);
            changedTicking = false;
        }
    }
}
