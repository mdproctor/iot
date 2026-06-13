package io.casehub.iot.testing;

import java.time.Instant;

public final class DeviceFixtureDefaults {

    public static final DeviceFixtureDefaults DEFAULT =
        new DeviceFixtureDefaults(Fixtures.DEFAULT_TENANT, Fixtures.EPOCH, true);

    private final String tenancyId;
    private final Instant lastUpdated;
    private final boolean available;

    public DeviceFixtureDefaults(String tenancyId, Instant lastUpdated, boolean available) {
        this.tenancyId = tenancyId;
        this.lastUpdated = lastUpdated;
        this.available = available;
    }

    public String tenancyId() { return tenancyId; }
    public Instant lastUpdated() { return lastUpdated; }
    public boolean available() { return available; }
}
