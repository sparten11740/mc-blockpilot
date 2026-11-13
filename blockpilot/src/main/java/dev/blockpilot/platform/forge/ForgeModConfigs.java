package dev.blockpilot.platform.forge;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import dev.blockpilot.platform.ModConfigs;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.IConfigEvent;
import net.minecraftforge.fml.config.ModConfig;

/** The configs mods registered with a {@link ForgeConfigSpec}, which nearly all Forge mods use. */
final class ForgeModConfigs implements ModConfigs {
    @Override
    public boolean available() {
        return true;
    }

    @Override
    public List<File> files() {
        List<File> files = new ArrayList<>();
        for (ModConfig config : ConfigTracker.INSTANCE.fileMap().values()) {
            if (config.getSpec() instanceof ForgeConfigSpec spec) {
                files.add(new SpecFile(config, spec));
            }
        }
        files.sort(Comparator.comparing(File::modId).thenComparing(File::type).thenComparing(File::fileName));
        return files;
    }

    private record SpecFile(ModConfig config, ForgeConfigSpec spec) implements File {
        @Override
        public String modId() {
            return config.getModId();
        }

        @Override
        public String type() {
            return config.getType().name().toLowerCase(Locale.ROOT);
        }

        @Override
        public String fileName() {
            return config.getFileName();
        }

        @Override
        public boolean loaded() {
            return spec.isLoaded();
        }

        @Override
        public List<Entry> entries() {
            List<Entry> entries = new ArrayList<>();
            collect(spec.getValues(), entries);
            return entries;
        }

        /** The values nest by section, as the file's tables do. */
        private void collect(UnmodifiableConfig values, List<Entry> entries) {
            for (Object value : values.valueMap().values()) {
                if (value instanceof ForgeConfigSpec.ConfigValue<?> configValue) {
                    entries.add(new SpecEntry(config, spec.getSpec().get(configValue.getPath()), configValue));
                } else if (value instanceof UnmodifiableConfig section) {
                    collect(section, entries);
                }
            }
        }
    }

    private record SpecEntry(ModConfig config, ForgeConfigSpec.ValueSpec valueSpec,
        ForgeConfigSpec.ConfigValue<?> configValue) implements Entry {
        @Override
        public String key() {
            return String.join(".", configValue.getPath());
        }

        @Override
        public Object value() {
            // The value the file and later sets hold; get() can keep a cached one.
            Object raw = config.getConfigData() == null ? null : config.getConfigData().get(configValue.getPath());
            return raw != null ? raw : configValue.get();
        }

        @Override
        public Object defaultValue() {
            return configValue.getDefault();
        }

        @Override
        public Object min() {
            return bound("getMin");
        }

        @Override
        public Object max() {
            return bound("getMax");
        }

        /** Forge 47 keeps its range type private, so its public bounds are read reflectively. */
        private Object bound(String getter) {
            Object range = valueSpec.getRange();
            if (range == null) {
                return null;
            }
            try {
                Method method = range.getClass().getMethod(getter);
                method.setAccessible(true);
                return method.invoke(range);
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }

        @Override
        public String comment() {
            return valueSpec.getComment();
        }

        @Override
        public String restart() {
            // Forge knows only whether a change waits for the world to reload.
            return valueSpec.needsWorldRestart() ? "world" : "none";
        }

        @Override
        public boolean accepts(Object value) {
            return valueSpec.test(value);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void set(Object value) {
            ((ForgeConfigSpec.ConfigValue<Object>) configValue).set(value);
            // What FML sends after re-reading an edited file; mods that copy values into fields do it here.
            ModList.get().getModContainerById(config.getModId())
                .ifPresent(container -> container.dispatchConfigEvent(IConfigEvent.reloading(config)));
        }
    }
}
