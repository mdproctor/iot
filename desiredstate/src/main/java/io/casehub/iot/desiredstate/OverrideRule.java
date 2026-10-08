package io.casehub.iot.desiredstate;

import io.casehub.iot.api.TriggerSource;

import java.util.Objects;

public record OverrideRule(
        TriggerSource trigger,
        String source,
        String targets,
        RevertConfig revert
) {
    public OverrideRule {
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(revert, "revert");
    }
}
