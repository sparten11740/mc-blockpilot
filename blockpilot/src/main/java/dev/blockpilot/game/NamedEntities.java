package dev.blockpilot.game;

import dev.blockpilot.api.CommandException;
import dev.blockpilot.game.common.Sides;
import dev.blockpilot.game.observe.AwayCamera;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The entity an {@code @<name>} means: one tagged with the name or whose custom name it is, ignoring case, nearest
 * where the server takes the player to be. Tags live only on the server, so the client asks the integrated server for
 * them when it may, and on a remote server only custom names match.
 */
final class NamedEntities {
    private NamedEntities() {
    }

    static OptionalInt find(String name) throws CommandException {
        Minecraft minecraft = Minecraft.getInstance();
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server != null && server.isSameThread()) {
            ServerPlayer player = ServerCommands.serverPlayer();
            ServerLevel level = ServerCommands.serverLevel();
            return nearest(matching(level.getAllEntities(), name), player == null ? Vec3.ZERO
                : AwayCamera.presence(player));
        }
        if (minecraft.level == null || minecraft.player == null) {
            throw new CommandException("not_in_world");
        }
        List<Entity> found = matching(minecraft.level.entitiesForRendering(), name);
        List<Integer> tagged = Sides.tryServer(level -> ids(matching(((ServerLevel) level).getAllEntities(), name)));
        if (tagged != null) {
            for (int id : tagged) {
                Entity entity = minecraft.level.getEntity(id);
                if (entity != null && !found.contains(entity)) {
                    found.add(entity);
                }
            }
        }
        Vec3 centre = AwayCamera.centre();
        return nearest(found, centre != null ? centre : minecraft.player.position());
    }

    private static List<Entity> matching(Iterable<Entity> entities, String name) {
        List<Entity> found = new ArrayList<>();
        for (Entity entity : entities) {
            if (tags(entity).contains(name)
                || entity.getCustomName() != null && entity.getCustomName().getString().equalsIgnoreCase(name)) {
                found.add(entity);
            }
        }
        return found;
    }

    private static Set<String> tags(Entity entity) {
        //? if >=26.1 {
        /*return entity.entityTags();
        *///?} else {
        return entity.getTags();
        //?}
    }

    private static List<Integer> ids(List<Entity> entities) {
        return entities.stream().map(Entity::getId).toList();
    }

    private static OptionalInt nearest(List<Entity> entities, Vec3 from) {
        Entity nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity entity : entities) {
            double distance = entity.position().distanceToSqr(from);
            if (distance < best) {
                best = distance;
                nearest = entity;
            }
        }
        return nearest == null ? OptionalInt.empty() : OptionalInt.of(nearest.getId());
    }
}
