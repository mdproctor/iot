package io.casehub.iot.desiredstate;

import java.util.List;

public record PresetInfo(String name, List<String> imports, int deviceCount, List<OverrideRule> overrideRules) {
    public PresetInfo {
        imports = imports != null ? List.copyOf(imports) : List.of();
        overrideRules = overrideRules != null ? List.copyOf(overrideRules) : List.of();
    }

    public PresetInfo(String name, List<String> imports, int deviceCount) {
        this(name, imports, deviceCount, List.of());
    }
}
