package dev.blockpilot.game.log;

import dev.blockpilot.api.Event;
import dev.blockpilot.api.Fields;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;

/**
 * Keeps the game's recent log (INFO and above, from every thread and mod) in memory so agents can read it by
 * level, logger, text and position (a mark), with stack traces condensed to what matters.
 */
public final class LogCapture extends AbstractAppender {
    static final int CAPACITY = 5000;
    private static final String NAME = "Blockpilot capture";
    private static final List<String> SKIPPED_FRAMES = List.of("java.", "jdk.", "sun.", "org.apache.logging.",
        "org.slf4j.", "com.mojang.logging.");

    /** One captured log line; {@code trace} is condensed, {@code fullTrace} complete (both null without one). */
    public record Entry(long seq, long time, String level, String thread, String logger, String message,
        String trace, String fullTrace) {
    }

    private static final LogCapture INSTANCE = new LogCapture();
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final Deque<Entry> ENTRIES = new ArrayDeque<>();
    private static volatile Consumer<Event> sink = event -> { };

    private LogCapture() {
        // The one constructor every log4j has, from the 2.8 that Forge 1.20.1 compiles against to the 2.2x of 26.x.
        super(NAME, null, null, true);
    }

    /** Attaches the appender to the root logger; safe to call again after the logging setup was reloaded. */
    public static synchronized void install() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = context.getConfiguration();
        if (configuration.getRootLogger().getAppenders().containsKey(NAME)) {
            return;
        }
        if (!INSTANCE.isStarted()) {
            INSTANCE.start();
        }
        configuration.addAppender(INSTANCE);
        configuration.getRootLogger().addAppender(INSTANCE, Level.INFO, null);
        context.updateLoggers();
    }

    public static void setSink(Consumer<Event> value) {
        sink = value;
    }

    @Override
    public void append(LogEvent event) {
        Throwable thrown = event.getThrown();
        Entry entry = new Entry(SEQUENCE.incrementAndGet(), event.getTimeMillis(), event.getLevel().name(),
            event.getThreadName(), event.getLoggerName(), event.getMessage().getFormattedMessage(),
            thrown == null ? null : condensed(thrown), thrown == null ? null : full(thrown));
        synchronized (ENTRIES) {
            if (ENTRIES.size() >= CAPACITY) {
                ENTRIES.removeFirst();
            }
            ENTRIES.addLast(entry);
        }
        sink.accept(Event.of("log", new Fields().bare("level", entry.level()).bare("logger", entry.logger())
            .bare("message", entry.message()).hidden("seq", entry.seq())));
    }

    /** The newest sequence number, for {@code log mark}. */
    public static long mark() {
        return SEQUENCE.get();
    }

    public static List<Entry> since(long sequence) {
        synchronized (ENTRIES) {
            List<Entry> out = new ArrayList<>();
            for (Entry entry : ENTRIES) {
                if (entry.seq() > sequence) {
                    out.add(entry);
                }
            }
            return out;
        }
    }

    public static Entry get(long sequence) {
        synchronized (ENTRIES) {
            for (Entry entry : ENTRIES) {
                if (entry.seq() == sequence) {
                    return entry;
                }
            }
            return null;
        }
    }

    /** Class and message, three frames outside the JDK and logging, and each cause on one line. */
    static String condensed(Throwable thrown) {
        StringBuilder out = new StringBuilder();
        Throwable current = thrown;
        boolean first = true;
        while (current != null) {
            out.append(first ? "" : " | caused by ").append(current.getClass().getName());
            if (current.getMessage() != null) {
                out.append(": ").append(current.getMessage());
            }
            if (first) {
                int shown = 0;
                for (StackTraceElement frame : current.getStackTrace()) {
                    if (shown == 3) {
                        break;
                    }
                    if (SKIPPED_FRAMES.stream().noneMatch(frame.getClassName()::startsWith)) {
                        out.append(" at ").append(frame);
                        shown++;
                    }
                }
            }
            first = false;
            current = current.getCause() == current ? null : current.getCause();
        }
        return out.toString();
    }

    private static String full(Throwable thrown) {
        StringWriter text = new StringWriter();
        thrown.printStackTrace(new PrintWriter(text));
        return text.toString();
    }
}
