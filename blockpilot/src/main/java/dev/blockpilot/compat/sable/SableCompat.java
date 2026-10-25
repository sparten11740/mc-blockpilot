package dev.blockpilot.compat.sable;

import dev.blockpilot.game.sable.SubLevels;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.impl.DefaultSableCompanion;

/** Sub-levels through Sable's companion API; {@code SableSupport} loads it by name once Sable checks out. */
public final class SableCompat implements SubLevels {
    /** Fails when the companion has only its default implementation, i.e. Sable registered none. */
    public SableCompat() {
        SableCompanion companion = SableCompanion.INSTANCE;
        if (companion == null || companion instanceof DefaultSableCompanion) {
            throw new IllegalStateException("Sable registered no sub-level implementation");
        }
    }
}
