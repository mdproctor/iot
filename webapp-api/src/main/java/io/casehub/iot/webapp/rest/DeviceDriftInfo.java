package io.casehub.iot.webapp.rest;

import java.time.Instant;

public record DeviceDriftInfo(
        DriftStatus status,
        String triggerSource,
        Instant exemptUntil,
        String presetName
) {}
