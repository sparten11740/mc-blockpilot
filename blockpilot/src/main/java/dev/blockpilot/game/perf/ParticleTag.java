package dev.blockpilot.game.perf;

/** A particle's type, set when the particle engine creates it, so timings and counts can go by type. */
public interface ParticleTag {
    String blockpilot$type();

    void blockpilot$type(String type);

    /** The particle type id it was spawned from, else its class name (mods may add particles directly). */
    static String of(Object particle) {
        String type = ((ParticleTag) particle).blockpilot$type();
        return type != null ? type : particle.getClass().getSimpleName();
    }
}
