package io.casehub.iot.api.bridge;

import java.util.Objects;

public record FilterContext(
    String tenancyId,
    ConnectionState connectionState,
    String providerId
) {
    public FilterContext {
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(connectionState, "connectionState");
        Objects.requireNonNull(providerId, "providerId");
    }
}
