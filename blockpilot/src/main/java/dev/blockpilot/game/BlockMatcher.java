package dev.blockpilot.game;

import dev.blockpilot.api.CommandException;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Which blocks a token means: an id ({@code oak_log}), a tag ({@code #logs}) or an id pattern ({@code *_ore}), each
 * optionally with states ({@code oak_log[axis=y]}).
 */
public final class BlockMatcher implements Predicate<BlockState> {
    private final Predicate<BlockState> test;
    private final Map<String, String> states;
    /** Answers per block state, which Minecraft shares between all blocks in that state, so a scan asks once each. */
    private final Map<BlockState, Boolean> cache = new IdentityHashMap<>();

    private BlockMatcher(Predicate<BlockState> test, Map<String, String> states) {
        this.test = test;
        this.states = states;
    }

    public static BlockMatcher parse(String token) throws CommandException {
        BlockToken parsed = BlockToken.of(token);
        String what = parsed.block();
        if (what.startsWith("#")) {
            TagKey<Block> tag = TagKey.create(Registries.BLOCK, Ids.parse(what.substring(1)));
            return new BlockMatcher(state -> state.is(tag), parsed.states());
        }
        if (what.contains("*")) {
            return new BlockMatcher(idMatching(List.of(what)), parsed.states());
        }
        Block block = BuiltInRegistries.BLOCK.getOptional(Ids.parse(what))
            .orElseThrow(() -> new CommandException("syntax", "unknown block " + what));
        check(block, parsed.states());
        return new BlockMatcher(state -> state.is(block), parsed.states());
    }

    /** Matches ids or {@code *} patterns, e.g. the configured notable blocks. */
    public static BlockMatcher anyOf(List<String> patterns) {
        return new BlockMatcher(idMatching(patterns), Map.of());
    }

    private static Predicate<BlockState> idMatching(List<String> patterns) {
        List<Pattern> compiled = patterns.stream()
            .map(pattern -> Pattern.compile(("\\Q" + pattern + "\\E").replace("*", "\\E.*\\Q")))
            .toList();
        return state -> {
            String id = Ids.of(state);
            return compiled.stream().anyMatch(pattern -> pattern.matcher(id).matches());
        };
    }

    /** Fails unless the block has each state and each state takes its value, naming what the block has. */
    private static void check(Block block, Map<String, String> states) throws CommandException {
        StateDefinition<Block, BlockState> definition = block.getStateDefinition();
        for (Map.Entry<String, String> state : states.entrySet()) {
            Property<?> property = definition.getProperty(state.getKey());
            if (property == null) {
                List<String> names = definition.getProperties().stream().map(Property::getName).toList();
                throw new CommandException("syntax", Ids.of(block) + (names.isEmpty() ? " has no states"
                    : " has no state " + state.getKey() + "; its states are " + String.join(", ", names)));
            }
            List<String> values = valueNames(property);
            if (!values.contains(state.getValue())) {
                throw new CommandException("syntax", state.getKey() + " of " + Ids.of(block) + " takes "
                    + String.join("|", values) + ", not " + state.getValue());
            }
        }
    }

    private static <T extends Comparable<T>> List<String> valueNames(Property<T> property) {
        return property.getPossibleValues().stream().map(property::getName).toList();
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    /** Whether the block state holds every wanted value; a block without one of the states doesn't. */
    private boolean holdsStates(BlockState state) {
        for (Map.Entry<String, String> wanted : states.entrySet()) {
            Property<?> property = state.getBlock().getStateDefinition().getProperty(wanted.getKey());
            if (property == null || !valueName(state, property).equals(wanted.getValue())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean test(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        Boolean known = cache.get(state);
        if (known == null) {
            known = test.test(state) && holdsStates(state);
            cache.put(state, known);
        }
        return known;
    }
}
