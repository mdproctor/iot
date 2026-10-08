package io.casehub.iot.webapp.rest;

import io.casehub.iot.api.TriggerSource;

import java.time.Instant;

public record DriftExemptionGrantedEvent(
        String tenancyId,
        String deviceId,
        DriftStatus status,
        TriggerSource trigger,
        Instant exemptUntil,
        String presetName
) {}
