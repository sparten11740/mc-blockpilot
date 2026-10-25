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

/** Which blocks a token means: an id ({@code oak_log}), a tag ({@code #logs}) or an id pattern ({@code *_ore}). */
public final class BlockMatcher implements Predicate<BlockState> {
    private final Predicate<BlockState> test;
    private final Map<Block, Boolean> cache = new IdentityHashMap<>();

    private BlockMatcher(Predicate<BlockState> test) {
        this.test = test;
    }

    public static BlockMatcher parse(String token) throws CommandException {
        if (token.startsWith("#")) {
            TagKey<Block> tag = TagKey.create(Registries.BLOCK, Ids.parse(token.substring(1)));
            return new BlockMatcher(state -> state.is(tag));
        }
        if (token.contains("*")) {
            return anyOf(List.of(token));
        }
        Block block = BuiltInRegistries.BLOCK.getOptional(Ids.parse(token))
            .orElseThrow(() -> new CommandException("syntax", "unknown block " + token));
        return new BlockMatcher(state -> state.is(block));
    }

    /** Matches ids or {@code *} patterns, e.g. the configured notable blocks. */
    public static BlockMatcher anyOf(List<String> patterns) {
        List<Pattern> compiled = patterns.stream()
            .map(pattern -> Pattern.compile(("\\Q" + pattern + "\\E").replace("*", "\\E.*\\Q")))
            .toList();
        return new BlockMatcher(state -> {
            String id = Ids.of(state);
            return compiled.stream().anyMatch(pattern -> pattern.matcher(id).matches());
        });
    }

    @Override
    public boolean test(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        Block block = state.getBlock();
        Boolean known = cache.get(block);
        if (known == null) {
            known = test.test(state);
            cache.put(block, known);
        }
        return known;
    }
}
