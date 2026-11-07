package dev.blockpilot.gametest;

import java.util.Collection;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Rotation;

/**
 * Blockpilot's own GameTests on 1.21.1, for the smoke check of {@code gametest run}: one passes and one fails on
 * purpose. Only the dev runs include this source set. Their template, an empty structure, is written into the world
 * by the smoke check. They go into the registry ready-made, as Fabric's mixin turns no annotated method into a test
 * for a class its entrypoint didn't name.
 */
public final class BlockpilotSmokeTests {
    private static final String TEMPLATE = "blockpilot:smoke_empty";
    private static final int MAX_TICKS = 100;

    private BlockpilotSmokeTests() {
    }

    public static void addTo(Collection<TestFunction> registry) {
        registry.add(test("passes", GameTestHelper::succeed));
        registry.add(test("fails", helper -> helper.fail("fails on purpose, so the smoke check reads a failed row")));
    }

    private static TestFunction test(String name, Consumer<GameTestHelper> body) {
        return new TestFunction("defaultBatch", "blockpilotsmoketests." + name, TEMPLATE, Rotation.NONE, MAX_TICKS, 0L,
            true, false, 1, 1, false, body);
    }
}
