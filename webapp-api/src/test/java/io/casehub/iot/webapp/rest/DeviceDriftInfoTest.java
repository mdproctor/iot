package io.casehub.iot.webapp.rest;

import io.casehub.iot.api.TriggerSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceDriftInfoTest {

    @Test
    void convergedDeviceHasNoTriggerInfo() {
        var info = new DeviceDriftInfo(DriftStatus.CONVERGED, null, null, null);
        assertThat(info.status()).isEqualTo(DriftStatus.CONVERGED);
        assertThat(info.triggerSource()).isNull();
        assertThat(info.exemptUntil()).isNull();
    }

    @Test
    void permittedDriftCarriesTriggerAndExpiry() {
        Instant expiry = Instant.now().plusSeconds(1800);
        var info = new DeviceDriftInfo(DriftStatus.PERMITTED_DRIFT,
                TriggerSource.MOTION.name(), expiry, "night-mode");
        assertThat(info.status()).isEqualTo(DriftStatus.PERMITTED_DRIFT);
        assertThat(info.triggerSource()).isEqualTo("MOTION");
        assertThat(info.exemptUntil()).isEqualTo(expiry);
        assertThat(info.presetName()).isEqualTo("night-mode");
    }

    @Test
    void unexpectedDriftHasNoPreset() {
        var info = new DeviceDriftInfo(DriftStatus.UNEXPECTED_DRIFT, null, null, null);
        assertThat(info.status()).isEqualTo(DriftStatus.UNEXPECTED_DRIFT);
        assertThat(info.presetName()).isNull();
    }

    @Test
    void grantedEventCarriesAllFields() {
        Instant expiry = Instant.now().plusSeconds(1800);
        var event = new DriftExemptionGrantedEvent("tenant-1", "light-1",
                DriftStatus.PERMITTED_DRIFT, TriggerSource.MOTION, expiry, "night-mode");
        assertThat(event.tenancyId()).isEqualTo("tenant-1");
        assertThat(event.deviceId()).isEqualTo("light-1");
        assertThat(event.trigger()).isEqualTo(TriggerSource.MOTION);
        assertThat(event.presetName()).isEqualTo("night-mode");
    }

    @Test
    void revokedEventCarriesTenancyAndDevice() {
        var event = new DriftExemptionRevokedEvent("tenant-1", "light-1");
        assertThat(event.tenancyId()).isEqualTo("tenant-1");
        assertThat(event.deviceId()).isEqualTo("light-1");
    }
}
