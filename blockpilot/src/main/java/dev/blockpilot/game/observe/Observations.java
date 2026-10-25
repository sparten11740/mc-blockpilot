package dev.blockpilot.game.observe;

import dev.blockpilot.api.Event;
import dev.blockpilot.api.Fields;
import dev.blockpilot.game.GameClock;
import dev.blockpilot.game.Ids;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /** One sound start; {@code ui} sounds play at the listener (menus, many item sounds), not at a position. */
    public record Sound(long tick, String id, String category, Vec3 position, boolean ui, float volume, float pitch) {
    }

    public record Message(long tick, String kind, String text) {
    }

    private static final Deque<Sound> SOUND_LOG = new ArrayDeque<>();
    private static final Deque<Message> MESSAGE_LOG = new ArrayDeque<>();
    private static final LinkedHashMap<Long, Map<String, Integer>> PARTICLES = new LinkedHashMap<>();
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
        String category = sound.getSource().getName();
        Sound entry = new Sound(tick(), Ids.of(sound.getLocation()), category,
            new Vec3(sound.getX(), sound.getY(), sound.getZ()), sound.isRelative(), sound.getVolume(), sound.getPitch());
        if (SOUND_LOG.size() >= SOUNDS) {
            SOUND_LOG.removeFirst();
        }
        SOUND_LOG.addLast(entry);
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
            if (MESSAGE_LOG.size() >= MESSAGES) {
                MESSAGE_LOG.removeFirst();
            }
            MESSAGE_LOG.addLast(new Message(now, kind, text));
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
            oldest.next();
            oldest.remove();
        }
    }

    public static synchronized List<Sound> sounds(long since) {
        List<Sound> out = new ArrayList<>();
        for (Sound sound : SOUND_LOG) {
            if (sound.tick() >= since) {
                out.add(sound);
            }
        }
        return out;
    }

    public static synchronized List<Message> messages(long since) {
        List<Message> out = new ArrayList<>();
        for (Message message : MESSAGE_LOG) {
            if (message.tick() >= since) {
                out.add(message);
            }
        }
        return out;
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
