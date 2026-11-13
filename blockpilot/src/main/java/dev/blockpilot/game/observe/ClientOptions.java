package dev.blockpilot.game.observe;

import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.Numbers;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.sounds.SoundSource;
//? if >=1.21.11 {
/*import net.minecraft.client.GraphicsPreset;
import net.minecraft.server.level.ParticleStatus;
*///?} else {
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.ParticleStatus;
//?}

/**
 * The client options agents may change, by their {@code options.txt} names, and the originals of the ones they
 * changed. Values go through {@link OptionInstance#set}, so vanilla's update hooks run, plus what the video settings
 * screen does after some changes (rebuilding the chunks for graphics, reloading textures for mipmaps); nothing saves
 * {@code options.txt}. An option can change others (a graphics preset sets a dozen, and changing one of those makes the
 * preset custom); those count as changed by the agent too. Client thread only.
 */
public final class ClientOptions {
    /** One option, read and written as text. */
    interface Setting {
        String name();

        String value();

        /** What the option takes, e.g. {@code fast|fancy|fabulous}, {@code on|off} or {@code 2..32}. */
        String accepts();

        /** Parses the text and sets it; {@code err syntax} naming what the option takes when it doesn't fit. */
        void set(String text) throws CommandException;

        /** Puts the current value back when run. */
        Runnable snapshot();
    }

    /** An option backed by an {@link OptionInstance}. */
    private abstract static class Instance<T> implements Setting {
        private final String name;
        private final Function<Options, OptionInstance<T>> option;
        /** What the video settings screen does after a change, beyond the option's own update hook. */
        private Consumer<T> afterChange = value -> { };

        Instance(String name, Function<Options, OptionInstance<T>> option) {
            this.name = name;
            this.option = option;
        }

        Instance<T> afterChange(Consumer<T> hook) {
            afterChange = hook;
            return this;
        }

        abstract T parse(String text) throws CommandException;

        abstract String text(T value);

        OptionInstance<T> instance() {
            return option.apply(Minecraft.getInstance().options);
        }

        CommandException rejected(String text) {
            return new CommandException("syntax", name + " takes " + accepts() + ", not " + text);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String value() {
            return text(instance().get());
        }

        @Override
        public void set(String text) throws CommandException {
            apply(parse(text));
        }

        @Override
        public Runnable snapshot() {
            T value = instance().get();
            return () -> apply(value);
        }

        private void apply(T value) {
            T before = instance().get();
            instance().set(value);
            if (!before.equals(instance().get())) {
                afterChange.accept(instance().get());
            }
        }
    }

    private static final class Toggle extends Instance<Boolean> {
        Toggle(String name, Function<Options, OptionInstance<Boolean>> option) {
            super(name, option);
        }

        @Override
        Boolean parse(String text) throws CommandException {
            return switch (text) {
                case "on", "true" -> true;
                case "off", "false" -> false;
                default -> throw rejected(text);
            };
        }

        @Override
        String text(Boolean value) {
            return value ? "on" : "off";
        }

        @Override
        public String accepts() {
            return "on|off";
        }
    }

    /** An enum option, written as the lower-case constant name; only constants the validator accepts. */
    private static final class Choice<E extends Enum<E>> extends Instance<E> {
        private final Class<E> type;

        Choice(String name, Function<Options, OptionInstance<E>> option, Class<E> type) {
            super(name, option);
            this.type = type;
        }

        private List<E> allowed() {
            OptionInstance<E> instance = instance();
            return Arrays.stream(type.getEnumConstants())
                .filter(constant -> instance.values().validateValue(constant).isPresent()).toList();
        }

        @Override
        E parse(String text) throws CommandException {
            for (E constant : allowed()) {
                if (text(constant).equals(text)) {
                    return constant;
                }
            }
            throw rejected(text);
        }

        @Override
        String text(E value) {
            return value.name().toLowerCase(Locale.ROOT);
        }

        @Override
        public String accepts() {
            return allowed().stream().map(this::text).collect(Collectors.joining("|"));
        }
    }

    /** Whole numbers. Some validators clamp instead of refusing, so a value counts only if it comes back unchanged. */
    private static final class Whole extends Instance<Integer> {
        private static final int LIMIT = 1 << 20;

        Whole(String name, Function<Options, OptionInstance<Integer>> option) {
            super(name, option);
        }

        private boolean valid(int value) {
            return instance().values().validateValue(value).filter(Integer.valueOf(value)::equals).isPresent();
        }

        @Override
        Integer parse(String text) throws CommandException {
            try {
                int value = Integer.parseInt(text);
                if (valid(value)) {
                    return value;
                }
            } catch (NumberFormatException e) {
                // Not a number: the same error as one out of range.
            }
            throw rejected(text);
        }

        @Override
        String text(Integer value) {
            return String.valueOf(value);
        }

        /** The range, bisected from the current value (the validators accept one contiguous range). */
        @Override
        public String accepts() {
            int current = instance().get();
            int low = -LIMIT;
            int high = current;
            while (low < high) {
                int middle = Math.floorDiv(low + high, 2);
                if (valid(middle)) {
                    high = middle;
                } else {
                    low = middle + 1;
                }
            }
            int min = low;
            low = current;
            high = LIMIT;
            while (low < high) {
                int middle = Math.floorDiv(low + high + 1, 2);
                if (valid(middle)) {
                    low = middle;
                } else {
                    high = middle - 1;
                }
            }
            return min + ".." + low;
        }
    }

    /** Decimal numbers; the validator may snap a value to its step, and the snapped value is set. */
    private static final class Decimal extends Instance<Double> {
        private static final double LIMIT = 1 << 20;
        private static final double PRECISION = 1e-4;

        Decimal(String name, Function<Options, OptionInstance<Double>> option) {
            super(name, option);
        }

        private Optional<Double> validated(double value) {
            return instance().values().validateValue(value);
        }

        @Override
        Double parse(String text) throws CommandException {
            try {
                Optional<Double> value = validated(Double.parseDouble(text));
                if (value.isPresent()) {
                    return value.get();
                }
            } catch (NumberFormatException e) {
                // Not a number: the same error as one out of range.
            }
            throw rejected(text);
        }

        @Override
        String text(Double value) {
            return Numbers.format(value);
        }

        /** The range, bisected from the current value to within the precision. */
        @Override
        public String accepts() {
            double current = instance().get();
            double low = -LIMIT;
            double high = current;
            while (high - low > PRECISION) {
                double middle = (low + high) / 2;
                if (validated(middle).isPresent()) {
                    high = middle;
                } else {
                    low = middle;
                }
            }
            double min = high;
            low = current;
            high = LIMIT;
            while (high - low > PRECISION) {
                double middle = (low + high) / 2;
                if (validated(middle).isPresent()) {
                    low = middle;
                } else {
                    high = middle;
                }
            }
            return Numbers.format(min) + ".." + Numbers.format(low);
        }
    }

    /** The F1 switch, which isn't an {@link OptionInstance}: {@code on} shows the GUI. */
    private static final class Hud implements Setting {
        @Override
        public String name() {
            return "hud";
        }

        @Override
        public String value() {
            return HudVisibility.hidden() ? "off" : "on";
        }

        @Override
        public String accepts() {
            return "on|off";
        }

        @Override
        public void set(String text) throws CommandException {
            switch (text) {
                case "on" -> HudVisibility.setHidden(false);
                case "off" -> HudVisibility.setHidden(true);
                default -> throw new CommandException("syntax", "hud takes on|off, not " + text);
            }
        }

        @Override
        public Runnable snapshot() {
            boolean hidden = HudVisibility.hidden();
            return () -> HudVisibility.setHidden(hidden);
        }
    }

    /** The value an option had before the first agent change, and how to put it back. */
    private record Original(String text, Runnable restore) {
    }

    /** In restore order: a graphics preset comes first, as restoring it sets the options it covers. */
    private static final Map<String, Setting> SETTINGS = new LinkedHashMap<>();
    private static final Map<String, Original> ORIGINALS = new HashMap<>();
    /** Unlisted options an agent change changed along the way, with how to put each back. */
    private static final Map<OptionInstance<?>, Runnable> UNLISTED = new LinkedHashMap<>();
    private static boolean changing;

    static {
        List<Setting> settings = List.of(
            //? if >=1.21.11 {
            /*new Choice<>("graphicsPreset", Options::graphicsPreset, GraphicsPreset.class),
            new Toggle("cutoutLeaves", Options::cutoutLeaves),
            new Toggle("improvedTransparency", Options::improvedTransparency),
            *///?} else {
            new Choice<>("graphicsMode", Options::graphicsMode, GraphicsStatus.class)
                .afterChange(value -> Minecraft.getInstance().levelRenderer.allChanged()),
            //?}
            new Choice<>("particles", Options::particles, ParticleStatus.class),
            new Whole("renderDistance", Options::renderDistance),
            new Whole("simulationDistance", Options::simulationDistance),
            new Choice<>("renderClouds", Options::cloudStatus, CloudStatus.class),
            new Toggle("ao", Options::ambientOcclusion),
            new Toggle("entityShadows", Options::entityShadows),
            new Whole("biomeBlendRadius", Options::biomeBlendRadius),
            new Whole("mipmapLevels", Options::mipmapLevels).afterChange(value -> {
                Minecraft.getInstance().updateMaxMipLevel(value);
                Minecraft.getInstance().delayTextureReload();
            }),
            new Decimal("entityDistanceScaling", Options::entityDistanceScaling),
            new Whole("maxFps", Options::framerateLimit),
            new Toggle("enableVsync", Options::enableVsync),
            new Choice<>("prioritizeChunkUpdates", Options::prioritizeChunkUpdates, PrioritizeChunkUpdates.class),
            new Whole("fov", Options::fov),
            new Decimal("gamma", Options::gamma),
            new Whole("guiScale", Options::guiScale),
            new Toggle("bobView", Options::bobView),
            new Decimal("fovEffectScale", Options::fovEffectScale),
            new Decimal("screenEffectScale", Options::screenEffectScale),
            new Decimal("darknessEffectScale", Options::darknessEffectScale),
            new Decimal("damageTiltStrength", Options::damageTiltStrength),
            new Toggle("hideLightningFlashes", Options::hideLightningFlash),
            new Decimal("glintStrength", Options::glintStrength),
            new Decimal("glintSpeed", Options::glintSpeed),
            new Hud());
        settings.forEach(setting -> SETTINGS.put(setting.name(), setting));
        for (SoundSource source : SoundSource.values()) {
            Setting volume = new Decimal("soundCategory_" + source.getName(),
                options -> options.getSoundSourceOptionInstance(source));
            SETTINGS.put(volume.name(), volume);
        }
    }

    private ClientOptions() {
    }

    static List<Setting> all() {
        return List.copyOf(SETTINGS.values());
    }

    static Setting setting(String name) throws CommandException {
        Setting setting = SETTINGS.get(name);
        if (setting == null) {
            throw new CommandException("syntax", "unknown option " + name + "; option lists them");
        }
        return setting;
    }

    /** The value before the first agent change, or null when agents haven't changed the option. */
    static String original(Setting setting) {
        Original original = ORIGINALS.get(setting.name());
        return original == null ? null : original.text();
    }

    /** Sets the option from text, remembering the original of every option that changes the first time. */
    static void set(Setting setting, String text) throws CommandException {
        Map<String, Original> before = new LinkedHashMap<>();
        SETTINGS.values().forEach(each -> before.put(each.name(), new Original(each.value(), each.snapshot())));
        changing = true;
        try {
            setting.set(text);
        } finally {
            changing = false;
        }
        before.forEach((name, original) -> {
            if (!original.text().equals(SETTINGS.get(name).value())) {
                ORIGINALS.putIfAbsent(name, original);
            }
        });
        tellServer();
    }

    /**
     * Sends the server the client's information, the render distance among it, which decides how far around the
     * player the server sends chunks. The game sends it when it saves options.txt, which agent changes never do.
     */
    private static void tellServer() {
        Minecraft.getInstance().options.broadcastOptions();
    }

    /** Called from a mixin before the game sets an option as a side effect of another (a graphics preset). */
    public static <T> void beforeSideEffect(OptionInstance<T> option) {
        boolean listed = SETTINGS.values().stream()
            .anyMatch(setting -> setting instanceof Instance<?> instance && instance.instance() == option);
        if (changing && !listed) {
            T value = option.get();
            UNLISTED.putIfAbsent(option, () -> option.set(value));
        }
    }

    /** Puts back every option agents changed, in list order, then the unlisted ones; returns the listed names. */
    public static List<String> restoreAll() {
        List<String> restored = SETTINGS.keySet().stream().filter(ORIGINALS::containsKey).toList();
        restored.forEach(name -> ORIGINALS.get(name).restore().run());
        UNLISTED.values().forEach(Runnable::run);
        ORIGINALS.clear();
        UNLISTED.clear();
        if (!restored.isEmpty()) {
            tellServer();
        }
        return restored;
    }
}
