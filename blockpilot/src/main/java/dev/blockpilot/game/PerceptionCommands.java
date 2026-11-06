package dev.blockpilot.game;

import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.EntityRef;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Numbers;
import dev.blockpilot.api.Position;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import dev.blockpilot.game.common.BlockFrame;
import dev.blockpilot.game.common.BlockMatcher;
import dev.blockpilot.game.common.BlockScan;
import dev.blockpilot.game.common.ClientScreens;
import dev.blockpilot.game.common.Ids;
import dev.blockpilot.game.common.Sides;
import dev.blockpilot.game.observe.AwayCamera;
import dev.blockpilot.game.observe.CameraControl;
import dev.blockpilot.game.observe.EntityUpdates;
import dev.blockpilot.game.sable.Rel;
import dev.blockpilot.game.sable.SableSupport;
import dev.blockpilot.game.sable.SubLevels;
import dev.blockpilot.protocol.CommandRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Read-only commands: what the player can perceive. None of them change the game. */
final class PerceptionCommands {
    private static final int MAX_FIND_RADIUS = 128;
    private static final int MAX_REGION_BLOCKS = 4096;
    private static final List<EquipmentSlot> ARMOR =
        List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    private final BlockpilotClient client;
    private BlockMatcher notable;

    PerceptionCommands(BlockpilotClient client) {
        this.client = client;
    }

    void register(CommandRegistry registry) {
        registry.register(CommandSpec.query("status", (context, args) -> status())
            .group("perception").help("the player's state on one line; 'menu' outside a world")
            .field("pos", "position").field("yaw", "degrees").field("pitch", "degrees").field("dim", "dimension")
            .field("time", "time of day").field("hp", "health").field("max_hp", "maximum health")
            .field("food", "food level").field("level", "xp level").field("hand", "held item")
            .field("aim", "block or entity under the crosshair")
            .field("hit", "where the crosshair meets the block, u,v,w in its own frame as face/use … at take it")
            .field("sub", "with Sable: the sub-level the aimed block is aboard; its position prints absolute")
            .field("world", "with Sable: where that block is in the world").field("vel", "velocity, blocks per tick")
            .field("on_ground", "standing on something").field("vehicle", "what the player rides")
            .field("camera", "a placed camera's absolute position, where the last frame drew it")
            .field("cam_fov", "a placed camera's own vertical field of view in degrees (camera … fov=)")
            .field("aboard", "with Sable: the sub-level the player stands on or rides")
            .example("status"));
        registry.register(CommandSpec.query("look", this::look).needsWorld().diffable()
            .group("perception").optionalArg("radius", ArgType.INT)
            .help("nearby entities and notable blocks (block entities, ores, …) within radius (default 16, max 64) of "
                + "the player, or of a camera placed with away; positions stay relative to the player's block")
            .field("count", "rows").example("look 12"));
        registry.register(CommandSpec.query("entity", this::entity).needsWorld().group("perception")
            .form(form -> form.arg("ref", List.of("me"), ArgType.ENTITY))
            .option("side", "server: the integrated server's copy of the entity (cheats on)", Sides.NAMES)
            .help("one entity's motion (me: the player): type, e<id>, position, yaw, pitch, velocity, age and, for "
                + "a living entity, its walk animation; with "
                + "side=server as the integrated server has it, so sampling both sides shows how far the client's "
                + "copy drifts")
            .field("type", "entity type").field("ref", "its e<id>").field("pos", "absolute position, to the thousandth")
            .field("yaw", "degrees, to the tenth").field("pitch", "degrees, to the tenth")
            .field("vel", "velocity, blocks per tick; left out at zero").field("on_ground", "false: airborne")
            .field("age", "ticks this copy has existed; a copy made again starts at 0")
            .field("walk", "living entities: how fast the limbs swing in the walk animation, 0 at rest")
            .field("walk_pos", "living entities: how far through their swing the limbs are")
            .field("net", "client only: updates the client applied since its previous tick, per kind (move, "
                + "teleport, motion, data), as net move1 data1; left out when none")
            .field("uuid", "side=server only").example("entity e12 side=server"));
        registry.register(CommandSpec.query("find", this::find).needsWorld().group("perception")
            .form(form -> form.arg("what", ArgType.TAG, ArgType.PATTERN, ArgType.ID, ArgType.BLOCK_STATE)
                .optional(range -> range.arg("radius", ArgType.INT).optionalArg("limit", ArgType.INT)))
            .option("side", "server: read the integrated server's level (cheats on), incl. chunks the client lacks",
                Sides.NAMES)
            .help("nearest blocks (id, #tag or *pattern, each also with states as Minecraft writes them: "
                + "oak_log[axis=y]), entities or dropped items, in loaded chunks within a cube reaching radius blocks "
                + "each way from the player, or a camera placed with away (default 32, max " + MAX_FIND_RADIUS
                + "), the nearest limit of them (default 5, max " + BlockScan.MAX_FIND_LIMIT + "); with Sable, blocks "
                + "aboard a sub-level are in its plot, which sable:find searches. A ~ position is relative to the "
                + "player's block as the find runs")
            .field("count", "matches in all, when the limit left some out")
            .field("at_abs", "JSON: each row's position, absolute")
            .field("radius", "the radius searched, when the one asked for was over the max")
            .example("find #logs 32 3"));
        registry.register(CommandSpec.query("block", this::block).needsWorld().group("perception")
            .arg("pos", ArgType.POSITION).optionalKeyword("light", "light")
            .option("side", "server: read the integrated server's level (cheats on)", Sides.NAMES)
            .help("one block: id, every state property with its value (defaults too), block entity type; light adds "
                + "the light the level stores for the cell, each 0 to 15, sky light before the time of day dims it; "
                + "with Sable, for a plot position also the light at the world cell where the block is drawn")
            .field("id", "block id").field("props", "every state property with its value")
            .field("be", "block entity type")
            .field("sub", "with Sable: the sub-level whose plot holds the block")
            .field("world", "with Sable: where that block is in the world")
            .field("sky", "with light: the cell's sky light").field("block_light", "with light: the cell's block light")
            .field("world_sky", "with light, for a plot position: sky light at the world cell where it is drawn")
            .field("world_block_light", "with light, for a plot position: block light at that world cell")
            .example("block ~0,-1,0"));
        registry.register(CommandSpec.query("blocks", this::blocks).needsWorld().group("perception")
            .arg("from", ArgType.POSITION).arg("to", ArgType.POSITION).optionalKeyword("grid", "grid")
            .option("side", "server: read the integrated server's level (cheats on)", Sides.NAMES)
            .help("a region (max 4096 blocks): palette, then Y layers bottom-up, rows north to south, RLE, . = air; "
                + "with grid, one symbol per cell instead of runs, a line per Y layer and row")
            .example("blocks ~-2,-1,-2 ~2,0,2"));
        registry.register(CommandSpec.query("inv", this::inventory).needsWorld().diffable().group("perception")
            .optionalKeyword("detail", "detail")
            .help("non-empty slots: *selected hotbar, 0-8 hotbar, 9-35 main, head/chest/legs/feet, offhand")
            .example("inv"));
    }

    private static Minecraft minecraft() {
        return Minecraft.getInstance();
    }

    private Result status() {
        Minecraft minecraft = minecraft();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            String screen = ClientScreens.current() == null ? "none" : Screens.type(ClientScreens.current());
            return Result.ok(new Fields().bare("state", "menu").bare("screen", screen));
        }
        Fields fields = new Fields()
            .put("pos", Rel.absolute(player.position()), "pos " + Rel.absolute(player.position()))
            .tag("yaw", Math.round(Mth.wrapDegrees(player.getYRot())))
            .tag("pitch", Math.round(player.getXRot()))
            .bare("dim", Ids.of(minecraft.level.dimension().location()))
            .put("time", minecraft.level.getDayTime() % 24000, "daytime" + minecraft.level.getDayTime() % 24000)
            .hidden("hp", round(player.getHealth())).hidden("max_hp", round(player.getMaxHealth()))
            .put("health", Numbers.format(round(player.getHealth())) + "/" + Numbers.format(round(player.getMaxHealth())),
                "hp" + Numbers.format(round(player.getHealth())) + "/" + Numbers.format(round(player.getMaxHealth())))
            .tag("food", player.getFoodData().getFoodLevel())
            .put("level", player.experienceLevel, "lv" + player.experienceLevel);
        GameType mode = minecraft.gameMode == null ? GameType.SURVIVAL : minecraft.gameMode.getPlayerMode();
        if (mode != GameType.SURVIVAL) {
            fields.bare("mode", mode.getName());
        }
        ItemStack hand = player.getMainHandItem();
        if (!hand.isEmpty()) {
            fields.put("hand", Ids.of(hand), "hand:" + Ids.of(hand) + (hand.getCount() > 1 ? " x" + hand.getCount() : ""));
        }
        aim(minecraft.hitResult, fields);
        Vec3 velocity = player.getDeltaMovement();
        boolean resting = player.onGround() && Math.abs(velocity.x) < 0.003 && Math.abs(velocity.z) < 0.003;
        if (!resting) {
            Position vel = Position.absolute(round3(velocity.x), round3(velocity.y), round3(velocity.z));
            fields.put("vel", vel, "vel " + vel);
        }
        if (!player.onGround()) {
            fields.put("on_ground", false, player.getAbilities().flying ? "flying" : "airborne");
        }
        SubLevels.Info aboard = SableSupport.subLevels().carrying(player);
        if (aboard != null) {
            fields.put("aboard", aboard.name(), "aboard:" + aboard.name());
        }
        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            fields.put("vehicle", Ids.of(vehicle), "riding:" + Ids.of(vehicle) + " e" + vehicle.getId());
        }
        List<String> effects = new ArrayList<>();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            effect.getEffect().unwrapKey().ifPresent(key ->
                effects.add(Ids.of(key.location()) + (effect.getAmplifier() + 1)));
        }
        if (!effects.isEmpty()) {
            fields.put("effects", effects, "effects:" + String.join(",", effects));
        }
        if (player.getAirSupply() < player.getMaxAirSupply()) {
            fields.put("air", player.getAirSupply(), "air" + player.getAirSupply() + "/" + player.getMaxAirSupply());
        }
        if (player.getArmorValue() > 0) {
            fields.tag("armor", player.getArmorValue());
        }
        flag(fields, player.isOnFire(), "burning");
        flag(fields, player.isInWater(), "in_water");
        flag(fields, player.isSleeping(), "sleeping");
        flag(fields, player.isShiftKeyDown(), "sneaking");
        flag(fields, player.isDeadOrDying(), "dead");
        if (ClientScreens.current() != null) {
            fields.put("screen", Screens.type(ClientScreens.current()), "screen:" + Screens.type(ClientScreens.current()));
        }
        Position placedAt = CameraControl.placement() != null ? CameraControl.frameCamera() : null;
        if (placedAt != null) {
            fields.put("camera", placedAt, "camera " + placedAt);
        }
        Double placedFov = CameraControl.placedFov();
        if (placedFov != null) {
            double fov = Math.round(placedFov * 10) / 10.0;
            fields.put("cam_fov", fov, "cam_fov=" + Numbers.format(fov));
        }
        CameraControl.Angles camera = CameraControl.rendered();
        if (camera != null && (Math.abs(Mth.wrapDegrees(camera.yaw() - player.getYRot())) > 0.5
            || Math.abs(camera.pitch() - player.getXRot()) > 0.5 || Math.abs(camera.roll()) > 0.5
            || CameraControl.placement() != null)) {
            fields.put("cam_yaw", Math.round(Mth.wrapDegrees(camera.yaw()) * 10) / 10.0, "cam:"
                    + Numbers.format(Math.round(Mth.wrapDegrees(camera.yaw()) * 10) / 10.0) + "/"
                    + Numbers.format(Math.round(camera.pitch() * 10) / 10.0) + "/"
                    + Numbers.format(Math.round(camera.roll() * 10) / 10.0))
                .hidden("cam_pitch", Math.round(camera.pitch() * 10) / 10.0)
                .hidden("cam_roll", Math.round(camera.roll() * 10) / 10.0);
        }
        return Result.ok(fields);
    }

    /** What the crosshair hits, as the game itself computed it this frame (works for blocks drawn anywhere). */
    static void aim(HitResult hit, Fields fields) {
        Level level = minecraft().level;
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK && level != null) {
            String id = Ids.of(level.getBlockState(block.getBlockPos()));
            fields.put("aim", id, "aim:" + id).bare("aim_pos", Rel.of(block.getBlockPos()));
            Rel.sub(fields, level, block.getBlockPos());
            fields.put("face", block.getDirection().getName(), "face:" + block.getDirection().getName())
                .put("hit", hitPoint(level, block)).hidden("aim_at", Rel.absolute(hit.getLocation()));
        } else if (hit instanceof EntityHitResult entity && hit.getType() == HitResult.Type.ENTITY) {
            fields.put("aim", Ids.of(entity.getEntity()), "aim:" + Ids.of(entity.getEntity()))
                .bare("aim_entity", new EntityRef(entity.getEntity().getId()))
                .hidden("aim_at", Rel.absolute(hit.getLocation()));
        }
    }

    /** Where the crosshair meets the block, in the block's own frame as {@code face/use … at} take it. */
    private static Position hitPoint(Level level, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        Vec3 location = hit.getLocation();
        BlockFrame.Point point = frame(level, pos).fromBlock(new BlockFrame.Point(location.x - pos.getX(),
            location.y - pos.getY(), location.z - pos.getZ()));
        return Position.absolute(withinBlock(point.x()), withinBlock(point.y()), withinBlock(point.z()));
    }

    /** Two decimals, kept inside the block against rounding at its faces. */
    private static double withinBlock(double coordinate) {
        return Numbers.round(Mth.clamp(coordinate, 0, 1));
    }

    private static void flag(Fields fields, boolean set, String name) {
        if (set) {
            fields.put(name, true, name);
        }
    }

    private static double round(float value) {
        return Math.round(value * 10) / 10.0;
    }

    private static double round3(double value) {
        return Math.round(value * 1000) / 1000.0;
    }

    /** Text replies print hundredths; motion compared between client and server needs the thousandths kept. */
    private static String thousandths(Position position) {
        return Stream.of(position.x(), position.y(), position.z())
            .map(value -> BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros())
            .map(BigDecimal::toPlainString).collect(Collectors.joining(","));
    }

    private BlockMatcher notable() {
        if (notable == null) {
            notable = BlockMatcher.anyOf(client.config().notableBlocks);
        }
        return notable;
    }

    private Result look(CommandContext context, Args args) {
        Minecraft minecraft = minecraft();
        LocalPlayer player = minecraft.player;
        int radius = Mth.clamp(args.integer("radius", 16), 1, 64);
        int cap = client.config().maxRows;
        List<Row> rows = new ArrayList<>();
        int more = 0;

        Vec3 here = AwayCamera.clientPresence();
        BlockPos center = BlockPos.containing(here);
        AABB box = player.getBoundingBox().move(here.subtract(player.position())).inflate(radius);
        List<Entity> entities = minecraft.level.getEntities(player, box,
            entity -> entity.isAlive() && !entity.isSpectator());
        entities.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(here)));
        for (Entity entity : entities) {
            if (rows.size() >= cap) {
                more++;
                continue;
            }
            rows.add(entityRow(entity));
        }

        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, BlockPos> nearest = new LinkedHashMap<>();
        BlockScan.around(minecraft.level, center, radius, notable(), true, (pos, state) -> {
            String id = Ids.of(state);
            counts.computeIfAbsent(id, key -> new int[1])[0]++;
            BlockPos best = nearest.get(id);
            if (best == null || pos.distSqr(center) < best.distSqr(center)) {
                nearest.put(id, pos.immutable());
            }
        });
        List<String> blockIds = new ArrayList<>(nearest.keySet());
        blockIds.sort(Comparator.comparingDouble(id -> nearest.get(id).distSqr(center)));
        int blockRows = 0;
        for (String id : blockIds) {
            if (blockRows >= cap) {
                more++;
                continue;
            }
            blockRows++;
            int count = counts.get(id)[0];
            rows.add(Row.of("b:" + id, new Fields().bare("block", id).bare("count", count)
                .put("near", Rel.of(nearest.get(id)), "near " + Rel.of(nearest.get(id)))
                .hidden("near_abs", Rel.absolute(nearest.get(id)))));
        }
        return Result.ok().withRows(rows, more);
    }

    static Row entityRow(Entity entity) {
        return entityRow(entity, false);
    }

    /** An entity as a row; read on the server side, it also carries the entity's UUID. */
    static Row entityRow(Entity entity, boolean server) {
        Fields fields = new Fields().bare("type", Ids.of(entity)).bare("ref", new EntityRef(entity.getId()))
            .bare("at", Rel.of(entity.position())).hidden("at_abs", Rel.absolute(entity.position()));
        if (server) {
            fields.put("uuid", entity.getUUID().toString(), "uuid:" + entity.getUUID());
        }
        if (entity.hasCustomName()) {
            fields.put("name", entity.getCustomName().getString(), "\"" + entity.getCustomName().getString() + "\"");
        }
        if (entity instanceof LivingEntity living) {
            fields.tag("hp", round(living.getHealth()));
            ItemStack held = living.getMainHandItem();
            if (!held.isEmpty()) {
                fields.put("holds", Ids.of(held), "holds:" + Ids.of(held));
            }
        }
        if (entity instanceof ItemEntity item) {
            ItemStack stack = item.getItem();
            fields.put("item", Ids.of(stack), Ids.of(stack) + (stack.getCount() > 1 ? " x" + stack.getCount() : ""));
        }
        if (entity.isPassenger()) {
            fields.put("riding", new EntityRef(entity.getVehicle().getId()), "riding:e" + entity.getVehicle().getId());
        }
        return Row.of("e" + entity.getId(), fields);
    }

    private Result find(CommandContext context, Args args) throws CommandException {
        LocalPlayer player = minecraft().player;
        String what = args.word("what");
        int asked = args.integer("radius", 32);
        int radius = Mth.clamp(asked, 1, MAX_FIND_RADIUS);
        int limit = Mth.clamp(args.integer("limit", 5), 1, BlockScan.MAX_FIND_LIMIT);
        var id = args.type("what") == ArgType.ID ? Ids.parse(what) : null;
        BlockMatcher matcher = id != null && !BuiltInRegistries.BLOCK.containsKey(id) ? null : BlockMatcher.parse(what);
        Vec3 from = AwayCamera.clientPresence();
        BlockPos center = BlockPos.containing(from);
        AABB box = new AABB(center).inflate(radius);
        Result found = Sides.read(args, (level, server) -> {
            var entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
            if (matcher == null && entityType.isPresent()) {
                var type = entityType.get();
                List<Entity> entities = level.getEntities((Entity) null, box,
                    entity -> entity.getType() == type && entity.isAlive() && entity.getId() != player.getId());
                entities.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(from)));
                return nearest(entities, limit, entity -> entityRow(entity, server));
            }
            if (matcher == null) {
                var item = BuiltInRegistries.ITEM.getOptional(id)
                    .orElseThrow(() -> new CommandException("syntax", "no block, item or entity type " + what));
                List<Entity> drops = level.getEntities((Entity) null, box,
                    entity -> entity instanceof ItemEntity drop && drop.getItem().is(item));
                drops.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(from)));
                return nearest(drops, limit, entity -> entityRow(entity, server));
            }
            List<BlockPos> blocks = new ArrayList<>();
            BlockScan.around(level, center, radius, matcher, false, (pos, state) -> blocks.add(pos.immutable()));
            blocks.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
            return nearest(blocks, limit, pos -> Row.of(pos.toShortString(), new Fields()
                .bare("block", Ids.of(level.getBlockState(pos))).bare("at", Rel.of(pos))
                .hidden("at_abs", Rel.absolute(pos))));
        });
        return asked > MAX_FIND_RADIUS && found.isOk() ? found.with(new Fields().put("radius", radius)) : found;
    }

    /** The first {@code limit} of the sorted matches, with {@code count} when the limit left some out. */
    private static <T> Result nearest(List<T> sorted, int limit, Function<T, Row> row) {
        Result result = sorted.size() > limit ? Result.ok(new Fields().put("count", sorted.size())) : Result.ok();
        return result.withRows(sorted.stream().limit(limit).map(row).toList());
    }

    private Result entity(CommandContext context, Args args) throws CommandException {
        boolean me = args.word("ref").equals("me");
        int id = me ? -1 : args.entity("ref").id();
        UUID playerId = minecraft().player.getUUID();
        return Sides.read(args, (level, server) -> {
            Entity entity = me ? level.getPlayerByUUID(playerId) : level.getEntity(id);
            if (entity == null) {
                throw new CommandException("no_entity", args.word("ref"));
            }
            Position pos = Position.absolute(round3(entity.getX()), round3(entity.getY()), round3(entity.getZ()));
            Fields fields = new Fields().bare("type", Ids.of(entity)).bare("ref", new EntityRef(entity.getId()))
                .put("pos", pos, "pos " + thousandths(pos))
                .tag("yaw", round(Mth.wrapDegrees(entity.getYRot()))).tag("pitch", round(entity.getXRot()));
            Vec3 velocity = entity.getDeltaMovement();
            Position vel = Position.absolute(round3(velocity.x), round3(velocity.y), round3(velocity.z));
            if (vel.x() != 0 || vel.y() != 0 || vel.z() != 0) {
                fields.put("vel", vel, "vel " + thousandths(vel));
            }
            if (!entity.onGround()) {
                fields.put("on_ground", false, "airborne");
            }
            fields.put("age", entity.tickCount, "age=" + entity.tickCount);
            if (entity instanceof LivingEntity living) {
                fields.put("walk", round3(living.walkAnimation.speed()))
                    .put("walk_pos", round3(living.walkAnimation.position()));
            }
            if (server) {
                fields.put("uuid", entity.getUUID().toString(), "uuid:" + entity.getUUID());
            } else {
                EntityUpdates.CLIENT.addTo(fields, entity.getId());
            }
            return Result.ok(fields);
        });
    }

    private Result block(CommandContext context, Args args) throws CommandException {
        BlockPos pos = Rel.block(context.resolve(args.position("pos")));
        String printed = Rel.of(pos).toString();
        boolean light = args.has("light");
        return Sides.read(args, (level, server) -> {
            if (!Sides.loaded(level, pos)) {
                throw new CommandException("unloaded", printed);
            }
            Fields fields = describe(level, pos);
            Rel.sub(fields, level, pos);
            if (light) {
                light(fields, level, pos);
            }
            return Result.ok(fields);
        });
    }

    /** The light stored for the cell and, for a cell in a sub-level's plot, for the world cell where it is drawn. */
    private static void light(Fields fields, Level level, BlockPos pos) {
        fields.put("sky", level.getBrightness(LightLayer.SKY, pos))
            .put("block_light", level.getBrightness(LightLayer.BLOCK, pos));
        if (SableSupport.subLevels().at(level, pos) != null) {
            BlockPos world = BlockPos.containing(SableSupport.subLevels().toWorld(level, Vec3.atCenterOf(pos)));
            // An unloaded chunk reads as full sky light.
            if (Sides.loaded(level, world)) {
                fields.put("world_sky", level.getBrightness(LightLayer.SKY, world))
                    .put("world_block_light", level.getBrightness(LightLayer.BLOCK, world));
            }
        }
    }

    /** A block's id, every property and block entity type, as {@code block} reports it. */
    static Fields describe(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Fields fields = new Fields().bare("id", Ids.of(state));
        Map<String, String> properties = allProperties(state);
        if (!properties.isEmpty()) {
            List<String> pairs = new ArrayList<>();
            properties.forEach((name, value) -> pairs.add(name + "=" + value));
            fields.put("props", properties, String.join(" ", pairs));
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            String type = Ids.of(blockEntity.getType());
            fields.put("be", type, "be:" + type);
        }
        return fields;
    }

    /** The block's own frame: turned by a horizontal {@code facing} or {@code horizontal_facing}, else unturned. */
    static BlockFrame frame(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        for (Property<?> property : state.getProperties()) {
            if ((property.getName().equals("facing") || property.getName().equals("horizontal_facing"))
                && state.getValue(property) instanceof Direction direction) {
                return BlockFrame.ofFacing(direction.getName());
            }
        }
        return BlockFrame.UNTURNED;
    }

    /** The properties that differ from the block's default state, as the {@code blocks} palette names them. */
    static Map<String, String> properties(BlockState state) {
        Map<String, String> properties = allProperties(state);
        Map<String, String> defaults = allProperties(state.getBlock().defaultBlockState());
        properties.entrySet().removeIf(property -> property.getValue().equals(defaults.get(property.getKey())));
        return properties;
    }

    /** Every property with its value, in the state's order. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static Map<String, String> allProperties(BlockState state) {
        Map<String, String> properties = new LinkedHashMap<>();
        for (Property property : state.getProperties()) {
            properties.put(property.getName(), property.getName(state.getValue(property)));
        }
        return properties;
    }

    private Result blocks(CommandContext context, Args args) throws CommandException {
        BlockPos from = Rel.block(context.resolve(args.position("from")));
        BlockPos to = Rel.block(context.resolve(args.position("to")));
        BlockPos min = new BlockPos(Math.min(from.getX(), to.getX()), Math.min(from.getY(), to.getY()),
            Math.min(from.getZ(), to.getZ()));
        BlockPos max = new BlockPos(Math.max(from.getX(), to.getX()), Math.max(from.getY(), to.getY()),
            Math.max(from.getZ(), to.getZ()));
        long volume = (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
        if (volume > MAX_REGION_BLOCKS) {
            throw new CommandException("too_large", String.valueOf(MAX_REGION_BLOCKS));
        }
        boolean grid = args.has("grid");
        return Sides.read(args, (level, server) -> grid ? Regions.grid(level, min, max)
            : Regions.dump(level, min, max));
    }

    private Result inventory(CommandContext context, Args args) {
        Inventory inventory = minecraft().player.getInventory();
        boolean detail = args.has("detail");
        List<Row> rows = new ArrayList<>();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                String name = (slot == InventoryOps.selected(inventory) ? "*" : "") + slot;
                rows.add(Row.of(String.valueOf(slot), Items.describe(name, stack, detail)));
            }
        }
        for (EquipmentSlot armor : ARMOR) {
            ItemStack stack = minecraft().player.getItemBySlot(armor);
            if (!stack.isEmpty()) {
                rows.add(Row.of(armor.getName(), Items.describe(armor.getName(), stack, detail)));
            }
        }
        ItemStack offhand = minecraft().player.getOffhandItem();
        if (!offhand.isEmpty()) {
            rows.add(Row.of("offhand", Items.describe("offhand", offhand, detail)));
        }
        return Result.ok().withRows(rows);
    }
}
