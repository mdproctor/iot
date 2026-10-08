package io.casehub.iot.desiredstate;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ActivePresetRegistry {

    private final Map<String, ActivePreset> active = new ConcurrentHashMap<>();

    public void set(String tenancyId, String presetName, List<OverrideRule> rules) {
        active.put(tenancyId, new ActivePreset(presetName, List.copyOf(rules)));
    }

    public Optional<ActivePreset> get(String tenancyId) {
        return Optional.ofNullable(active.get(tenancyId));
    }

    public void clear(String tenancyId) {
        active.remove(tenancyId);
    }

    public record ActivePreset(String name, List<OverrideRule> overrideRules) {}
}
