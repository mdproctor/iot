package io.casehub.iot.testing;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceFixtureDefaultsTest {

    @Test
    void constructorStoresAllFields() {
        var defaults = new DeviceFixtureDefaults("tenant-1",
            Instant.parse("2026-06-01T00:00:00Z"), false);
        assertThat(defaults.tenancyId()).isEqualTo("tenant-1");
        assertThat(defaults.lastUpdated()).isEqualTo(Instant.parse("2026-06-01T00:00:00Z"));
        assertThat(defaults.available()).isFalse();
    }

    @Test
    void defaultInstanceUsesFixtureConstants() {
        var defaults = DeviceFixtureDefaults.DEFAULT;
        assertThat(defaults.tenancyId()).isEqualTo(Fixtures.DEFAULT_TENANT);
        assertThat(defaults.lastUpdated()).isEqualTo(Fixtures.EPOCH);
        assertThat(defaults.available()).isTrue();
    }
}
