package dev.blockpilot.platform;

import java.util.List;

/** The mods' own config files as the loader keeps them, to read and set their values while the game runs. */
public interface ModConfigs {
    /** For a loader without a shared config system. */
    ModConfigs NONE = new ModConfigs() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public List<File> files() {
            return List.of();
        }
    };

    boolean available();

    /** Every mod's config files, by mod id, type and file name. */
    List<File> files();

    /** One config file of a mod. */
    interface File {
        String modId();

        /** {@code client}, {@code common}, {@code server} or {@code startup}. */
        String type();

        String fileName();

        /** False while the loader holds no values for it: a server config outside a world. */
        boolean loaded();

        /** The values in the order the mod defined them; call only while loaded. */
        List<Entry> entries();
    }

    /** One value of a config file. */
    interface Entry {
        /** The dotted path as in the file, e.g. {@code client.lights}. */
        String key();

        Object value();

        Object defaultValue();

        /** The bounds of the value's range, or null without one. */
        Object min();

        Object max();

        /** The mod's comment on the value, or null. */
        String comment();

        /** {@code none}, {@code world} or {@code game}: the restart a change waits for before the mod reads it. */
        String restart();

        /** Whether the mod's own check accepts the value. */
        boolean accepts(Object value);

        /** Sets the value in memory, without writing the file, and tells the mod as a reload of its file does. */
        void set(Object value);
    }
}
