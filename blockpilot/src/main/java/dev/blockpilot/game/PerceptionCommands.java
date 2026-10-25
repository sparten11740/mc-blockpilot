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
import dev.blockpilot.game.observe.CameraControl;
import dev.blockpilot.protocol.CommandRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Read-only commands: what the player can perceive. None of them change the game. */
final class PerceptionCommands {
    private static final int MAX_FIND_RADIUS = 128;
    private static final int MAX_REGION_BLOCKS = 4096;

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
            .field("aim", "block or entity under the crosshair").field("vel", "velocity, blocks per tick")
            .field("on_ground", "standing on something").field("vehicle", "what the player rides")
            .example("status"));
        registry.register(CommandSpec.query("look", this::look).needsWorld().diffable()
            .group("perception").optionalArg("radius", ArgType.INT)
            .help("nearby entities and notable blocks (block entities, ores, …) within radius (default 16, max 64)")
            .field("count", "rows").example("look 12"));
        registry.register(CommandSpec.query("find", this::find).needsWorld().group("perception")
            .arg("what", ArgType.TAG, ArgType.PATTERN, ArgType.ID).optionalArg("radius", ArgType.INT)
            .optionalArg("limit", ArgType.INT)
            .option("side", "server: read the integrated server's level (cheats on), incl. chunks the client lacks")
            .help("nearest blocks (id, #tag or *pattern), entities or dropped items, in loaded chunks")
            .field("count", "matches").example("find #logs 32 3"));
        registry.register(CommandSpec.query("block", this::block).needsWorld().group("perception")
            .arg("pos", ArgType.POSITION).option("side", "server: read the integrated server's level (cheats on)")
            .help("one block: id, non-default properties, block entity type")
            .field("id", "block id").field("props", "non-default state properties").field("be", "block entity type")
            .example("block ~0,-1,0"));
        registry.register(CommandSpec.query("blocks", this::blocks).needsWorld().group("perception")
            .arg("from", ArgType.POSITION).arg("to", ArgType.POSITION)
            .option("side", "server: read the integrated server's level (cheats on)")
            .help("a region (max 4096 blocks): palette, then Y layers bottom-up, rows north to south, RLE, . = air")
            .example("blocks ~-2,-1,-2 ~2,0,2"));
        registry.register(CommandSpec.query("inv", this::inventory).needsWorld().diffable().group("perception")
            .choice("detail", true, "detail")
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
            String screen = minecraft.screen == null ? "none" : Screens.type(minecraft.screen);
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
        if (minecraft.screen != null) {
            fields.put("screen", Screens.type(minecraft.screen), "screen:" + Screens.type(minecraft.screen));
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
            fields.put("aim", id, "aim:" + id)
                .bare("aim_pos", Rel.of(block.getBlockPos()))
                .put("face", block.getDirection().getName(), "face:" + block.getDirection().getName())
                .hidden("aim_at", Rel.absolute(hit.getLocation()));
        } else if (hit instanceof EntityHitResult entity && hit.getType() == HitResult.Type.ENTITY) {
            fields.put("aim", Ids.of(entity.getEntity()), "aim:" + Ids.of(entity.getEntity()))
                .bare("aim_entity", new EntityRef(entity.getEntity().getId()))
                .hidden("aim_at", Rel.absolute(hit.getLocation()));
        }
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

        List<Entity> entities = minecraft.level.getEntities(player, player.getBoundingBox().inflate(radius),
            entity -> entity.isAlive() && !entity.isSpectator());
        entities.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(player)));
        for (Entity entity : entities) {
            if (rows.size() >= cap) {
                more++;
                continue;
            }
            rows.add(entityRow(entity));
        }

        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, BlockPos> nearest = new LinkedHashMap<>();
        BlockPos center = player.blockPosition();
        scan(minecraft.level, center, radius, notable(), true, (pos, state) -> {
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

    /** Visits matching blocks in loaded chunks within the cube, skipping sections whose palette can't match. */
    static void scan(Level level, BlockPos center, int radius, BlockMatcher matcher, boolean withBlockEntities,
        BiConsumer<BlockPos, BlockState> visitor) {
        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - radius);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + radius);
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                if (withBlockEntities) {
                    for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                        BlockPos pos = blockEntity.getBlockPos();
                        if (inside(pos, minX, maxX, minY, maxY, minZ, maxZ)
                            && !matcher.test(blockEntity.getBlockState())) {
                            visitor.accept(pos, blockEntity.getBlockState());
                        }
                    }
                }
                LevelChunkSection[] sections = chunk.getSections();
                for (int index = 0; index < sections.length; index++) {
                    LevelChunkSection section = sections[index];
                    int sectionY = (chunk.getMinSection() + index) << 4;
                    if (section.hasOnlyAir() || sectionY + 15 < minY || sectionY > maxY
                        || !section.getStates().maybeHas(matcher)) {
                        continue;
                    }
                    for (int y = Math.max(minY, sectionY); y <= Math.min(maxY, sectionY + 15); y++) {
                        for (int x = Math.max(minX, chunkX << 4); x <= Math.min(maxX, (chunkX << 4) + 15); x++) {
                            for (int z = Math.max(minZ, chunkZ << 4); z <= Math.min(maxZ, (chunkZ << 4) + 15); z++) {
                                BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                                if (matcher.test(state)) {
                                    visitor.accept(cursor.set(x, y, z), state);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean inside(BlockPos pos, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        return pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY && pos.getY() <= maxY
            && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    private Result find(CommandContext context, Args args) throws CommandException {
        LocalPlayer player = minecraft().player;
        String what = args.word("what");
        int radius = Mth.clamp(args.integer("radius", 32), 1, MAX_FIND_RADIUS);
        int limit = Mth.clamp(args.integer("limit", 5), 1, 100);
        var id = args.kind("what") == ArgType.ID ? Ids.parse(what) : null;
        BlockMatcher matcher = id != null && !BuiltInRegistries.BLOCK.containsKey(id) ? null : BlockMatcher.parse(what);
        BlockPos center = player.blockPosition();
        Vec3 eyes = player.position();
        AABB box = new AABB(center).inflate(radius);
        return Sides.read(args, (level, server) -> {
            List<Row> rows = new ArrayList<>();
            if (matcher == null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                var type = BuiltInRegistries.ENTITY_TYPE.get(id);
                List<Entity> entities = level.getEntities((Entity) null, box,
                    entity -> entity.getType() == type && entity.isAlive() && entity.getId() != player.getId());
                entities.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(eyes)));
                entities.stream().limit(limit).forEach(entity -> rows.add(entityRow(entity, server)));
                return Result.ok().withRows(rows);
            }
            if (matcher == null) {
                if (!BuiltInRegistries.ITEM.containsKey(id)) {
                    throw new CommandException("syntax", "no block, item or entity type " + what);
                }
                var item = BuiltInRegistries.ITEM.get(id);
                List<Entity> drops = level.getEntities((Entity) null, box,
                    entity -> entity instanceof ItemEntity drop && drop.getItem().is(item));
                drops.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(eyes)));
                drops.stream().limit(limit).forEach(entity -> rows.add(entityRow(entity, server)));
                return Result.ok().withRows(rows);
            }
            List<BlockPos> found = new ArrayList<>();
            scan(level, center, radius, matcher, false, (pos, state) -> found.add(pos.immutable()));
            found.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
            for (BlockPos pos : found.subList(0, Math.min(limit, found.size()))) {
                rows.add(Row.of(pos.toShortString(), new Fields().bare("block", Ids.of(level.getBlockState(pos)))
                    .bare("at", Rel.of(pos)).hidden("at_abs", Rel.absolute(pos))));
            }
            return Result.ok().withRows(rows);
        });
    }

    private Result block(CommandContext context, Args args) throws CommandException {
        BlockPos pos = Rel.block(context.resolve(args.position("pos")));
        return Sides.read(args, (level, server) -> {
            if (!Sides.loaded(level, pos)) {
                throw new CommandException("unloaded", Rel.of(pos).toString());
            }
            return Result.ok(describe(level, pos));
        });
    }

    /** A block's id, non-default properties and block entity type, as {@code block} reports it. */
    static Fields describe(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Fields fields = new Fields().bare("id", Ids.of(state));
        Map<String, String> properties = properties(state);
        if (!properties.isEmpty()) {
            List<String> pairs = new ArrayList<>();
            properties.forEach((name, value) -> pairs.add(name + "=" + value));
            fields.put("props", properties, String.join(" ", pairs));
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            String type = Ids.of(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()));
            fields.put("be", type, "be:" + type);
        }
        return fields;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static Map<String, String> properties(BlockState state) {
        Map<String, String> properties = new LinkedHashMap<>();
        BlockState defaults = state.getBlock().defaultBlockState();
        for (Property property : state.getProperties()) {
            Comparable value = state.getValue(property);
            if (!value.equals(defaults.getValue(property))) {
                properties.put(property.getName(), property.getName(value));
            }
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
        return Sides.read(args, (level, server) -> Regions.dump(level, min, max));
    }

    private Result inventory(CommandContext context, Args args) {
        Inventory inventory = minecraft().player.getInventory();
        boolean detail = args.has("detail");
        List<Row> rows = new ArrayList<>();
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            ItemStack stack = inventory.items.get(slot);
            if (!stack.isEmpty()) {
                String name = (slot == inventory.selected ? "*" : "") + slot;
                rows.add(Row.of(String.valueOf(slot), Items.describe(name, stack, detail)));
            }
        }
        String[] armor = {"feet", "legs", "chest", "head"};
        for (int index = 3; index >= 0; index--) {
            ItemStack stack = inventory.armor.get(index);
            if (!stack.isEmpty()) {
                rows.add(Row.of(armor[index], Items.describe(armor[index], stack, detail)));
            }
        }
        ItemStack offhand = inventory.offhand.get(0);
        if (!offhand.isEmpty()) {
            rows.add(Row.of("offhand", Items.describe("offhand", offhand, detail)));
        }
        return Result.ok().withRows(rows);
    }
}
