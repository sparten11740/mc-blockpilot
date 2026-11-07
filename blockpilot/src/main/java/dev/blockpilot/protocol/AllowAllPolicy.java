package dev.blockpilot.protocol;

import java.util.List;
import java.util.UUID;

/**
 * dangerouslyAllowAll: every check answers yes. Adding a check to {@link SafetyPolicy} doesn't compile until this
 * policy answers it too, so it can't be forgotten here.
 */
public enum AllowAllPolicy implements SafetyPolicy {
    INSTANCE;

    @Override
    public boolean trusts(UUID player, Connection now) {
        return true;
    }

    @Override
    public boolean extensionAllowed(CommandRegistry.Entry entry) {
        return true;
    }

    @Override
    public boolean eventAllowed(String namespace) {
        return true;
    }

    @Override
    public boolean playOnly() {
        return false;
    }

    @Override
    public String indicatorNote() {
        return ", safety off";
    }

    @Override
    public List<String> trustedNames() {
        return List.of();
    }
}
