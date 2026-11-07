package dev.blockpilot.game.observe;

import dev.blockpilot.api.Event;
import dev.blockpilot.api.Fields;
import dev.blockpilot.game.common.GameClock;
import dev.blockpilot.game.common.Ids;
import dev.blockpilot.mixin.AbstractSoundInstanceAccessor;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * What the player would notice but text output otherwise misses: sounds, on-screen messages and particles. Each is
 * kept in a fixed-size ring, stamped with the client level's game time (the same clock the server ticks).
 */
public final class Observations {
    static final int SOUNDS = 2000;
    static final int MESSAGES = 500;
    static final int PARTICLE_TICKS = 1200;

    /**
     * One sound start; {@code ui} sounds play at the listener (menus, many item sounds), not at a position. Volume and
     * pitch are what it plays with; the asked ones are those it was played with, before its resource scaled them, and
     * null for a sound instance of a mod's own kind.
     */
    public record Sound(long tick, String id, String category, Vec3 position, boolean ui, float volume, float pitch,
        Float askedVolume, Float askedPitch) {
        /** The resolved sound as it is now. */
        public static Sound of(long tick, SoundInstance sound) {
            Float askedVolume = null;
            Float askedPitch = null;
            if (sound instanceof AbstractSoundInstanceAccessor own) {
                askedVolume = own.blockpilot$volume();
                askedPitch = own.blockpilot$pitch();
            }
            return new Sound(tick, Ids.of(sound.getLocation()), sound.getSource().getName(),
                new Vec3(sound.getX(), sound.getY(), sound.getZ()), sound.isRelative(), sound.getVolume(),
                sound.getPitch(), askedVolume, askedPitch);
        }
    }

    public record Message(long tick, String kind, String text) {
    }

    private static final TickLog<Sound> SOUND_LOG = new TickLog<>(SOUNDS, Sound::tick);
    private static final TickLog<Message> MESSAGE_LOG = new TickLog<>(MESSAGES, Message::tick);
    /** The tick each sound instance first started; a sound the engine loops by hand is played again each loop. */
    private static final Map<SoundInstance, Long> STARTED = new WeakHashMap<>();
    private static final LinkedHashMap<Long, Map<String, Integer>> PARTICLES = new LinkedHashMap<>();
    /** The latest tick whose particle counts were dropped, or null. */
    private static Long particlesDroppedThrough;
    /** The game tick the player last entered a world, or null before the first. */
    private static volatile Long joinedTick;
    private static final Map<String, String> LAST_TEXT = new HashMap<>();
    private static final Map<String, Long> LAST_TICK = new HashMap<>();
    private static final int REPEAT_TICKS = 40;
    private static volatile Consumer<Event> events = event -> { };

    private Observations() {
    }

    public static void setEventSink(Consumer<Event> sink) {
        events = sink;
    }

    /** The current tick (see {@link GameClock}), or -1 outside a world. */
    public static long tick() {
        return GameClock.now();
    }

    public static synchronized void sound(SoundInstance sound) {
        Sound entry = Sound.of(tick(), sound);
        SOUND_LOG.add(entry);
        STARTED.putIfAbsent(sound, entry.tick());
    }

    /** The tick the sound first started, or null if it never went through the engine's play. */
    public static synchronized Long started(SoundInstance sound) {
        return STARTED.get(sound);
    }

    /**
     * Logs a message; action bar, titles and subtitles are also sent as events (chat has its own event). Mods often
     * refresh the same action-bar text every tick, so a repeat within 2 seconds of its last refresh is not logged.
     */
    public static void message(String kind, Component message) {
        String text = message.getString();
        if (text.isBlank()) {
            return;
        }
        synchronized (Observations.class) {
            long now = tick();
            String previous = LAST_TEXT.put(kind, text);
            Long refreshed = LAST_TICK.put(kind, now);
            if (!kind.equals("chat") && text.equals(previous) && refreshed != null && now - refreshed < REPEAT_TICKS) {
                return;
            }
            MESSAGE_LOG.add(new Message(now, kind, text));
        }
        if (!kind.equals("chat")) {
            events.accept(Event.of(kind, new Fields().bare("text", text)));
        }
    }

    public static synchronized void particle(ParticleType<?> type) {
        long tick = tick();
        String id = Ids.of(BuiltInRegistries.PARTICLE_TYPE.getKey(type));
        PARTICLES.computeIfAbsent(tick, key -> new HashMap<>()).merge(id, 1, Integer::sum);
        Iterator<Long> oldest = PARTICLES.keySet().iterator();
        while (PARTICLES.size() > PARTICLE_TICKS && oldest.hasNext()) {
            particlesDroppedThrough = oldest.next();
            oldest.remove();
        }
    }

    /**
     * Notes the tick the player entered a world at, and starts the logs anew, as their ticks are that world's game
     * time, which another world, or a new one under an old name, counts afresh.
     */
    public static synchronized void joined(long tick) {
        joinedTick = tick;
        SOUND_LOG.clear();
        MESSAGE_LOG.clear();
        STARTED.clear();
        PARTICLES.clear();
        particlesDroppedThrough = null;
        LAST_TEXT.clear();
        LAST_TICK.clear();
    }

    public static Long joinedTick() {
        return joinedTick;
    }

    public static synchronized List<Sound> sounds(long since) {
        return SOUND_LOG.since(since);
    }

    /** See {@link TickLog#from}. */
    public static synchronized Long soundsFrom(long since) {
        return SOUND_LOG.from(since);
    }

    public static synchronized Long soundsOldest() {
        return SOUND_LOG.oldest();
    }

    public static synchronized List<Message> messages(long since) {
        return MESSAGE_LOG.since(since);
    }

    /** See {@link TickLog#from}. */
    public static synchronized Long messagesFrom(long since) {
        return MESSAGE_LOG.from(since);
    }

    public static synchronized Long messagesOldest() {
        return MESSAGE_LOG.oldest();
    }

    /** As {@link TickLog#from}, for the particle counts. */
    public static synchronized Long particlesFrom(long since) {
        return particlesDroppedThrough != null && since <= particlesDroppedThrough ? particlesOldest() : null;
    }

    public static synchronized Long particlesOldest() {
        return PARTICLES.isEmpty() ? null : PARTICLES.keySet().iterator().next();
    }

    /** Particle counts per type over the ticks from {@code since} on. */
    public static synchronized Map<String, Integer> particles(long since) {
        Map<String, Integer> totals = new HashMap<>();
        PARTICLES.forEach((tick, counts) -> {
            if (tick >= since) {
                counts.forEach((id, count) -> totals.merge(id, count, Integer::sum));
            }
        });
        return totals;
    }
}
