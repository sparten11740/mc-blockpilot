package dev.blockpilot.game.record;

import dev.blockpilot.Blockpilot;
import dev.blockpilot.mixin.SoundEngineAccessor;
import dev.blockpilot.mixin.SoundManagerAccessor;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;
import org.lwjgl.openal.ALCapabilities;
import org.lwjgl.openal.EXTDisconnect;
import org.lwjgl.openal.EXTThreadLocalContext;
import org.lwjgl.openal.EnumerateAllExt;
import org.lwjgl.openal.SOFTHRTF;
import org.lwjgl.openal.SOFTLoopback;
import org.lwjgl.openal.SOFTOutputLimiter;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * The game's sound through an OpenAL Soft loopback device while a recording wants it. The sound engine opens its device
 * as usual; {@code LibraryMixin} keeps that device here and gives the engine a loopback device instead, so the engine
 * runs unchanged. An audio thread renders the mix the wall clock is owed about every 10 ms, hands it to the recording,
 * and plays it on the real device through a context current on that thread only, so the game's context is untouched.
 */
public final class AudioCapture {
    /** Chunks stamped with the {@link System#nanoTime} their first frame plays at; called on the audio thread. */
    interface Listener {
        void chunk(long nanos, ByteBuffer frames);
    }

    private static final long NANOS = 1_000_000_000L;
    private static final long PERIOD_NANOS = 10_000_000L;
    private static final int MAX_CHUNK_FRAMES = WavWriter.RATE / 10;
    /** How far ahead of the real device the queue runs: sound past the most is dropped, an empty queue waits. */
    private static final int MIN_QUEUED_FRAMES = WavWriter.RATE * 40 / 1000;
    private static final int MAX_QUEUED_FRAMES = WavWriter.RATE * 120 / 1000;

    private static volatile boolean wanted;
    private static volatile Listener listener;
    /** Why the last loopback device didn't open, or null. */
    private static String problem;
    private static long loopback;
    private static long monitor;
    private static String monitorName;
    private static Thread renderer;
    private static volatile boolean rendering;
    /** On the real device, while it plays; audio thread only. */
    private static long played;
    private static long dropped;
    private static int starts;

    private AudioCapture() {
    }

    /** Why recording sound can't start now, or null; client thread. */
    static String unavailable() {
        if (!((SoundEngineAccessor) soundEngine()).blockpilot$loaded()) {
            return "the sound engine is off";
        }
        if (!ALC10.alcIsExtensionPresent(0L, "ALC_SOFT_loopback")) {
            return "OpenAL lacks loopback devices (ALC_SOFT_loopback)";
        }
        return null;
    }

    /**
     * Reopens the sound engine on a loopback device, or back on the real one, playing again the sounds that loop or
     * follow an entity, which would otherwise stay silent; client thread. Gives why it failed, or null.
     */
    static String use(boolean capture) {
        SoundEngine engine = soundEngine();
        List<SoundInstance> loops = ((SoundEngineAccessor) engine).blockpilot$channels().keySet().stream()
            .filter(sound -> sound.isLooping() || sound instanceof TickableSoundInstance).toList();
        wanted = capture;
        problem = null;
        engine.reload();
        for (SoundInstance sound : loops) {
            if (!(sound instanceof TickableSoundInstance tickable && tickable.isStopped())) {
                Minecraft.getInstance().getSoundManager().play(sound);
            }
        }
        if (capture && loopback == 0) {
            wanted = false;
            return problem != null ? problem : "the sound engine didn't start on a loopback device";
        }
        return null;
    }

    static void listen(Listener chunks) {
        listener = chunks;
    }

    /** The device the sound engine opened: kept to play on, with a loopback device returned in its place. */
    public static long device(long opened) {
        if (!wanted) {
            return opened;
        }
        long device = SOFTLoopback.alcLoopbackOpenDeviceSOFT((ByteBuffer) null);
        if (device == 0L || !SOFTLoopback.alcIsRenderFormatSupportedSOFT(device, WavWriter.RATE,
            SOFTLoopback.ALC_STEREO_SOFT, SOFTLoopback.ALC_SHORT_SOFT)) {
            if (device != 0L) {
                ALC10.alcCloseDevice(device);
            }
            problem = "OpenAL could not open a loopback device for 16-bit stereo at 48 kHz";
            return opened;
        }
        monitor = opened;
        monitorName = name(opened);
        loopback = device;
        Blockpilot.LOGGER.info("Sound goes through a loopback device for the recording, and on to {}", monitorName);
        return device;
    }

    public static boolean isLoopback(long device) {
        return device != 0L && device == loopback;
    }

    /** A loopback context needs its format; plain stereo, since a video plays on any speakers. */
    public static long context(long device) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer attributes = stack.ints(SOFTLoopback.ALC_FORMAT_CHANNELS_SOFT, SOFTLoopback.ALC_STEREO_SOFT,
                SOFTLoopback.ALC_FORMAT_TYPE_SOFT, SOFTLoopback.ALC_SHORT_SOFT, ALC10.ALC_FREQUENCY, WavWriter.RATE,
                SOFTHRTF.ALC_HRTF_SOFT, ALC10.ALC_FALSE, SOFTOutputLimiter.ALC_OUTPUT_LIMITER_SOFT, ALC10.ALC_TRUE, 0);
            return ALC10.alcCreateContext(device, attributes);
        }
    }

    /** The sound engine has started on that device. */
    public static void started(long device) {
        if (!isLoopback(device)) {
            return;
        }
        rendering = true;
        long playOn = monitor;
        renderer = new Thread(() -> render(device, playOn), "Blockpilot recording sound");
        renderer.setDaemon(true);
        renderer.start();
    }

    /** The sound engine is about to close that device: the audio thread stops first. */
    public static void closing(long device) {
        if (!isLoopback(device)) {
            return;
        }
        rendering = false;
        if (renderer != null) {
            try {
                renderer.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            renderer = null;
        }
        ALC10.alcCloseDevice(monitor);
        Blockpilot.LOGGER.info("Sound stops going through the recording's loopback device");
        loopback = 0L;
        monitor = 0L;
        monitorName = null;
    }

    /** The real device's name while the engine plays through a loopback device, else null. */
    public static String deviceName() {
        return loopback != 0L ? monitorName : null;
    }

    /** Whether the real device was lost, while the engine plays through a loopback device. */
    public static boolean monitorLost() {
        return ALC10.alcIsExtensionPresent(monitor, "ALC_EXT_disconnect")
            && ALC11.alcGetInteger(monitor, EXTDisconnect.ALC_CONNECTED) == 0;
    }

    private static void render(long device, long playOn) {
        boolean monitoring = ALC10.alcIsExtensionPresent(0L, "ALC_EXT_thread_local_context");
        long context = 0L;
        int source = 0;
        Deque<int[]> queued = new ArrayDeque<>();
        Deque<Integer> spare = new ArrayDeque<>();
        if (monitoring) {
            context = ALC10.alcCreateContext(playOn, (IntBuffer) null);
            EXTThreadLocalContext.alcSetThreadContext(context);
            ALCapabilities capabilities = AL.createCapabilities(ALC.createCapabilities(playOn));
            AL.setCurrentThread(capabilities);
            source = AL10.alGenSources();
        }
        ByteBuffer chunk = MemoryUtil.memAlloc(MAX_CHUNK_FRAMES * WavWriter.BYTES_PER_FRAME);
        played = 0;
        dropped = 0;
        starts = 0;
        try {
            long start = System.nanoTime();
            long rendered = 0;
            while (rendering) {
                long owed = Math.floorDiv((System.nanoTime() - start) * WavWriter.RATE, NANOS) - rendered;
                while (owed > 0) {
                    int frames = (int) Math.min(owed, MAX_CHUNK_FRAMES);
                    chunk.clear().limit(frames * WavWriter.BYTES_PER_FRAME);
                    SOFTLoopback.alcRenderSamplesSOFT(device, chunk, frames);
                    Listener chunks = listener;
                    if (chunks != null) {
                        chunks.chunk(start + rendered * NANOS / WavWriter.RATE, chunk.duplicate());
                    }
                    if (source != 0) {
                        play(source, chunk, frames, queued, spare);
                    }
                    rendered += frames;
                    owed -= frames;
                }
                LockSupport.parkNanos(PERIOD_NANOS);
            }
        } catch (RuntimeException e) {
            Blockpilot.LOGGER.error("Recording sound stopped", e);
        } finally {
            MemoryUtil.memFree(chunk);
            if (monitoring) {
                AL10.alSourceStop(source);
                AL10.alDeleteSources(source);
                queued.forEach(buffer -> AL10.alDeleteBuffers(buffer[0]));
                spare.forEach(AL10::alDeleteBuffers);
                AL.setCurrentThread(null);
                EXTThreadLocalContext.alcSetThreadContext(0L);
                ALC10.alcDestroyContext(context);
                // Each start after the first followed the queue running dry.
                Blockpilot.LOGGER.info("Played {} s of the recording's sound, dropped {} s, ran dry {} times",
                    played / WavWriter.RATE, dropped / WavWriter.RATE, Math.max(0, starts - 1));
            }
        }
    }

    /** Queues a chunk on the real device, keeping the queue within its band. */
    private static void play(int source, ByteBuffer chunk, int frames, Deque<int[]> queued, Deque<Integer> spare) {
        for (int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED); processed > 0; processed--) {
            spare.push(AL10.alSourceUnqueueBuffers(source));
            queued.removeFirst();
        }
        int ahead = queued.stream().mapToInt(buffer -> buffer[1]).sum();
        if (ahead + frames > MAX_QUEUED_FRAMES) {
            dropped += frames;
            return;
        }
        int buffer = spare.isEmpty() ? AL10.alGenBuffers() : spare.pop();
        AL10.alBufferData(buffer, AL10.AL_FORMAT_STEREO16, chunk.duplicate(), WavWriter.RATE);
        AL10.alSourceQueueBuffers(source, buffer);
        queued.addLast(new int[] {buffer, frames});
        played += frames;
        boolean stopped = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING;
        if (stopped && ahead + frames >= MIN_QUEUED_FRAMES) {
            AL10.alSourcePlay(source);
            starts++;
        }
    }

    private static String name(long device) {
        String name = ALC10.alcGetString(device, EnumerateAllExt.ALC_ALL_DEVICES_SPECIFIER);
        return name != null ? name : ALC10.alcGetString(device, ALC10.ALC_DEVICE_SPECIFIER);
    }

    private static SoundEngine soundEngine() {
        return ((SoundManagerAccessor) Minecraft.getInstance().getSoundManager()).blockpilot$soundEngine();
    }
}
