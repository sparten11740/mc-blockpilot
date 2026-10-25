package dev.blockpilot.compat.sable;

import dev.blockpilot.Blockpilot;
import dev.blockpilot.game.sable.Angles;
import dev.blockpilot.game.sable.SableIntegration;
import dev.blockpilot.game.sable.SubLevels;
import dev.blockpilot.protocol.CommandFailure;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.impl.DefaultSableCompanion;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;

/**
 * Sub-levels through Sable's companion API; {@code SableSupport} loads it by name once Sable checks out. Every call
 * into Sable goes through {@link #guard}, so a changed API fails the command instead of the game.
 */
public final class SableCompat implements SubLevels {
    /** The world border: sub-levels are seen inside it, wherever their plots are. */
    private static final double BORDER = 3.0E7;

    private final SableIntegration integration;
    private final SableCompanion companion;

    /** Fails when the companion has only its default implementation, i.e. Sable registered none. */
    public SableCompat(SableIntegration integration) {
        this.integration = integration;
        companion = SableCompanion.INSTANCE;
        if (companion == null || companion instanceof DefaultSableCompanion) {
            throw new IllegalStateException("Sable registered no sub-level implementation");
        }
    }

    @Override
    public Info at(Level level, BlockPos pos) {
        return guard("at", () -> {
            SubLevelAccess subLevel = companion.getContaining(level, (Vec3i) pos);
            return subLevel == null ? null : info(level, subLevel);
        });
    }

    @Override
    public Info info(Level level, UUID id) {
        return guard("info", () -> {
            SubLevelAccess subLevel = find(level, id);
            return subLevel == null ? null : info(level, subLevel);
        });
    }

    @Override
    public List<Info> near(Level level, AABB box) {
        return guard("near", () -> {
            List<Info> found = new ArrayList<>();
            companion.getAllIntersecting(level, new BoundingBox3d(box))
                .forEach(subLevel -> found.add(info(level, subLevel)));
            return found;
        });
    }

    @Override
    public Info carrying(Entity entity) {
        return guard("carrying", () -> {
            SubLevelAccess subLevel = companion.getTrackingOrVehicleSubLevel(entity);
            return subLevel == null ? null : info(entity.level(), subLevel);
        });
    }

    @Override
    public Vec3 toWorld(Level level, Vec3 point) {
        return guard("toWorld", () -> companion.projectOutOfSubLevel(level, (Position) point));
    }

    @Override
    public Vec3 velocity(Level level, Vec3 point) {
        return guard("velocity", () -> companion.getVelocity(level, (Position) point));
    }

    @Override
    public Vec3 toWorldRendered(Level level, Vec3 point, float partialTick) {
        return guard("toWorldRendered", () -> {
            SubLevelAccess subLevel = companion.getContaining(level, (Position) point);
            return subLevel instanceof ClientSubLevelAccess client
                ? client.renderPose(partialTick).transformPosition(point) : point;
        });
    }

    @Override
    public Vec3 toPlot(Level level, UUID id, Vec3 point) {
        return guard("toPlot", () -> {
            SubLevelAccess subLevel = find(level, id);
            return subLevel == null ? null : subLevel.logicalPose().transformPositionInverse(point);
        });
    }

    /**
     * A {@link LinkageError} means Sable's API changed under the compat package, so the integration turns off until
     * restart; anything else fails only this call. Either way the command fails with {@code err sable}.
     */
    private <T> T guard(String call, Supplier<T> body) {
        try {
            return body.get();
        } catch (LinkageError e) {
            integration.failed(call, e);
            throw new CommandFailure("sable", call + " failed: " + e + "; the Sable integration is off until restart");
        } catch (RuntimeException e) {
            Blockpilot.LOGGER.warn("Sable call {} failed", call, e);
            throw new CommandFailure("sable", call + " failed: " + e);
        }
    }

    private SubLevelAccess find(Level level, UUID id) {
        for (SubLevelAccess subLevel : companion.getAllIntersecting(level,
            new BoundingBox3d(-BORDER, -BORDER, -BORDER, BORDER, BORDER, BORDER))) {
            if (subLevel.getUniqueId().equals(id)) {
                return subLevel;
            }
        }
        return null;
    }

    private Info info(Level level, SubLevelAccess subLevel) {
        Pose3dc pose = subLevel.logicalPose();
        Vec3 position = vec(pose.position());
        Vec3 velocity = companion.getVelocity(level, subLevel, (Position) pose.transformPositionInverse(position));
        Angles angles = Angles.of(pose.orientation());
        UUID id = subLevel.getUniqueId();
        String name = subLevel.getName();
        return new Info(id, name == null || name.isEmpty() ? id.toString().substring(0, 8) : name, position, velocity,
            angles.yaw(), angles.pitch(), angles.roll(), subLevel.boundingBox().toMojang(),
            subLevel.boundingBox().transformInverse(pose, new BoundingBox3d()).toMojang());
    }

    private static Vec3 vec(Vector3dc vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }
}
