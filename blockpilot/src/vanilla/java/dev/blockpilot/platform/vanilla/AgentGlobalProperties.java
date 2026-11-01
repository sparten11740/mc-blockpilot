package dev.blockpilot.platform.vanilla;

import java.util.HashMap;
import java.util.Map;
import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IPropertyKey;

/** The properties Mixin shares across its parts; a launcher would keep them in its blackboard. */
public final class AgentGlobalProperties implements IGlobalPropertyService {
    private record Key(String name) implements IPropertyKey {
    }

    private final Map<String, Object> properties = new HashMap<>();

    @Override
    public IPropertyKey resolveKey(String name) {
        return new Key(name);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(IPropertyKey key) {
        return (T) properties.get(((Key) key).name());
    }

    @Override
    public void setProperty(IPropertyKey key, Object value) {
        properties.put(((Key) key).name(), value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(IPropertyKey key, T defaultValue) {
        return (T) properties.getOrDefault(((Key) key).name(), defaultValue);
    }

    @Override
    public String getPropertyString(IPropertyKey key, String defaultValue) {
        Object value = properties.get(((Key) key).name());
        return value != null ? value.toString() : defaultValue;
    }
}
