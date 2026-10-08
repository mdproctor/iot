package io.casehub.iot.webapp.rest;

public record DriftExemptionRevokedEvent(
        String tenancyId,
        String deviceId
) {}
