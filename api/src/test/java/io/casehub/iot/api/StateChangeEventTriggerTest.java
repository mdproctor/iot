package io.casehub.iot.api;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class StateChangeEventTriggerTest {

    static final class TestDevice extends DeviceEntity {
        private TestDevice(Builder builder) { super(builder); }
        static Builder builder() { return new Builder(); }
        static final class Builder extends DeviceEntity.Builder<TestDevice, Builder> {
            @Override protected Builder self() { return this; }
            @Override public TestDevice build() { return new TestDevice(this); }
        }
    }

    private TestDevice device() {
        return TestDevice.builder()
            .deviceId("d1")
            .deviceClass(DeviceClass.SWITCH)
            .label("Test")
            .available(true)
            .lastUpdated(Instant.parse("2026-06-07T10:00:00Z"))
            .tenancyId("tenant-1").providerId("test")
            .build();
    }

    @Test
    void legacyConstructorDefaultsToUnknownTrigger() {
        var event = new StateChangeEvent(null, device(), Set.of(), Instant.now(), "test");
        assertThat(event.triggerSource()).isEqualTo(TriggerSource.UNKNOWN);
    }

    @Test
    void fullConstructorPreservesTriggerSource() {
        var event = new StateChangeEvent(null, device(), Set.of(), Instant.now(), "test",
                TriggerSource.MOTION);
        assertThat(event.triggerSource()).isEqualTo(TriggerSource.MOTION);
    }

    @Test
    void nullTriggerSourceDefaultsToUnknown() {
        var event = new StateChangeEvent(null, device(), Set.of(), Instant.now(), "test",
                null);
        assertThat(event.triggerSource()).isEqualTo(TriggerSource.UNKNOWN);
    }

    @Test
    void triggerSourceEnumHasFiveValues() {
        assertThat(TriggerSource.values()).hasSize(5);
        assertThat(TriggerSource.valueOf("MOTION")).isNotNull();
        assertThat(TriggerSource.valueOf("MANUAL")).isNotNull();
        assertThat(TriggerSource.valueOf("SCHEDULE")).isNotNull();
        assertThat(TriggerSource.valueOf("AUTOMATION")).isNotNull();
        assertThat(TriggerSource.valueOf("UNKNOWN")).isNotNull();
    }
}
