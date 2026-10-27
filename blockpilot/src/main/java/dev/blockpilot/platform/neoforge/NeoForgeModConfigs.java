package dev.blockpilot.platform.neoforge;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import dev.blockpilot.platform.ModConfigs;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** The configs mods registered with a {@link ModConfigSpec}, which nearly all NeoForge mods use. */
final class NeoForgeModConfigs implements ModConfigs {
    @Override
    public boolean available() {
        return true;
    }

    @Override
    public List<File> files() {
        List<File> files = new ArrayList<>();
        for (ModConfig config : net.neoforged.fml.config.ModConfigs.getFileMap().values()) {
            if (config.getSpec() instanceof ModConfigSpec spec) {
                files.add(new SpecFile(config, spec));
            }
        }
        files.sort(Comparator.comparing(File::modId).thenComparing(File::type).thenComparing(File::fileName));
        return files;
    }

    private record SpecFile(ModConfig config, ModConfigSpec spec) implements File {
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
                if (value instanceof ModConfigSpec.ConfigValue<?> configValue) {
                    entries.add(new SpecEntry(config, configValue));
                } else if (value instanceof UnmodifiableConfig section) {
                    collect(section, entries);
                }
            }
        }
    }

    private record SpecEntry(ModConfig config, ModConfigSpec.ConfigValue<?> configValue) implements Entry {
        @Override
        public String key() {
            return String.join(".", configValue.getPath());
        }

        @Override
        public Object value() {
            // The value the file and later sets hold; get() keeps a restart value's old one until the restart.
            return configValue.getRaw();
        }

        @Override
        public Object defaultValue() {
            return configValue.getDefault();
        }

        @Override
        public Object min() {
            ModConfigSpec.Range<?> range = configValue.getSpec().getRange();
            return range == null ? null : range.getMin();
        }

        @Override
        public Object max() {
            ModConfigSpec.Range<?> range = configValue.getSpec().getRange();
            return range == null ? null : range.getMax();
        }

        @Override
        public String comment() {
            return configValue.getSpec().getComment();
        }

        @Override
        public String restart() {
            return configValue.getSpec().restartType().name().toLowerCase(Locale.ROOT);
        }

        @Override
        public boolean accepts(Object value) {
            return configValue.getSpec().test(value);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void set(Object value) {
            ((ModConfigSpec.ConfigValue<Object>) configValue).set(value);
            // What FML posts after re-reading an edited file; mods that copy values into fields do it here.
            ModList.get().getModContainerById(config.getModId())
                .ifPresent(container -> container.acceptEvent(new ModConfigEvent.Reloading(config)));
        }
    }
}
