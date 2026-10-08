package io.casehub.iot.desiredstate;

import io.casehub.desiredstate.api.Exemption;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.RevertCondition;
import io.casehub.desiredstate.api.RevertMode;
import io.casehub.desiredstate.testing.MockExemptionStore;
import io.casehub.iot.api.DeviceClass;
import io.casehub.iot.api.LightDevice;
import io.casehub.iot.api.LockDevice;
import io.casehub.iot.api.PresenceSensor;
import io.casehub.iot.api.StateChangeEvent;
import io.casehub.iot.api.TriggerSource;
import io.casehub.iot.testing.MockDeviceRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TriggerDriftOverrideObserverTest {

    private static final String TENANT = "tenant-1";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private MockExemptionStore exemptionStore;
    private ActivePresetRegistry presetRegistry;
    private IoTDriftPolicy driftPolicy;
    private MockDeviceRegistry deviceRegistry;
    private TriggerDriftOverrideObserver observer;

    @BeforeEach
    void setUp() {
        exemptionStore = new MockExemptionStore();
        presetRegistry = new ActivePresetRegistry();
        driftPolicy = IoTDriftPolicy.builder()
                .hardConstraint(DeviceClass.LOCK)
                .hardConstraint(DeviceClass.CAMERA)
                .build();
        deviceRegistry = new MockDeviceRegistry();
        observer = new TriggerDriftOverrideObserver(
                exemptionStore, presetRegistry, deviceRegistry, driftPolicy);
    }

    @Test
    void motionTriggerGrantsExemption() {
        deviceRegistry.addDevice(light("light-1", "hallway"));
        presetRegistry.set(TENANT, "night-mode", List.of(
                new OverrideRule(TriggerSource.MOTION, "sensor-1", "light-1",
                        new RevertConfig(RevertMode.DURATION, "30m"))));

        observer.onStateChange(triggerEvent(TriggerSource.MOTION, "sensor-1"));

        Optional<Exemption> exemption = exemptionStore.get(TENANT, NodeId.of("light-1-config"));
        assertThat(exemption).isPresent();
        assertThat(exemption.get().spec().revertCondition()).isInstanceOf(RevertCondition.OnDuration.class);
        assertThat(exemption.get().spec().metadata()).containsEntry("trigger", "MOTION");
        assertThat(exemption.get().spec().metadata()).containsEntry("preset", "night-mode");
    }

    @Test
    void unknownTriggerDoesNothing() {
        presetRegistry.set(TENANT, "night-mode", List.of(
                new OverrideRule(TriggerSource.MOTION, null, "light-1",
                        new RevertConfig(RevertMode.DURATION, "30m"))));

        observer.onStateChange(triggerEvent(TriggerSource.UNKNOWN, "light-1"));

        assertThat(exemptionStore.size()).isZero();
    }

    @Test
    void hardConstrainedDeviceNeverExempted() {
        deviceRegistry.addDevice(lock("lock-1"));
        presetRegistry.set(TENANT, "night-mode", List.of(
                new OverrideRule(TriggerSource.MANUAL, null, "lock-1",
                        new RevertConfig(RevertMode.DURATION, "60m"))));

        observer.onStateChange(triggerEvent(TriggerSource.MANUAL, "lock-1"));

        assertThat(exemptionStore.size()).isZero();
    }

    @Test
    void noActivePresetDoesNothing() {
        observer.onStateChange(triggerEvent(TriggerSource.MOTION, "sensor-1"));
        assertThat(exemptionStore.size()).isZero();
    }

    @Test
    void locationBasedTargetResolution() {
        deviceRegistry.addDevice(light("light-1", "hallway"));
        deviceRegistry.addDevice(light("light-2", "hallway"));
        deviceRegistry.addDevice(light("light-3", "bedroom"));
        presetRegistry.set(TENANT, "night-mode", List.of(
                new OverrideRule(TriggerSource.MOTION, null, "hallway/LIGHT",
                        new RevertConfig(RevertMode.DURATION, "30m"))));

        observer.onStateChange(triggerEvent(TriggerSource.MOTION, "sensor-hall"));

        assertThat(exemptionStore.getActive(TENANT)).hasSize(2);
    }

    @Test
    void sourceFilteringSkipsNonMatchingSource() {
        deviceRegistry.addDevice(light("light-1", "hallway"));
        presetRegistry.set(TENANT, "night-mode", List.of(
                new OverrideRule(TriggerSource.MOTION, "sensor-1", "light-1",
                        new RevertConfig(RevertMode.DURATION, "30m"))));

        observer.onStateChange(triggerEvent(TriggerSource.MOTION, "sensor-other"));

        assertThat(exemptionStore.size()).isZero();
    }

    @Test
    void nullSourceMatchesAnyTrigger() {
        deviceRegistry.addDevice(light("light-1", "hallway"));
        presetRegistry.set(TENANT, "night-mode", List.of(
                new OverrideRule(TriggerSource.MOTION, null, "light-1",
                        new RevertConfig(RevertMode.DURATION, "30m"))));

        observer.onStateChange(triggerEvent(TriggerSource.MOTION, "any-sensor"));

        assertThat(exemptionStore.get(TENANT, NodeId.of("light-1-config"))).isPresent();
    }

    private LightDevice light(String id, String location) {
        return new LightDevice.Builder()
                .deviceId(id).deviceClass(DeviceClass.LIGHT)
                .label(id).available(true).lastUpdated(NOW)
                .tenancyId(TENANT).providerId("test")
                .location(location)
                .on(false).build();
    }

    private LockDevice lock(String id) {
        return new LockDevice.Builder()
                .deviceId(id).deviceClass(DeviceClass.LOCK)
                .label(id).available(true).lastUpdated(NOW)
                .tenancyId(TENANT).providerId("test")
                .locked(true).build();
    }

    private StateChangeEvent triggerEvent(TriggerSource trigger, String deviceId) {
        PresenceSensor sensor = PresenceSensor.builder()
                .deviceId(deviceId).deviceClass(DeviceClass.PRESENCE_SENSOR)
                .label(deviceId).available(true).lastUpdated(NOW)
                .tenancyId(TENANT).providerId("test")
                .present(true).lastSeen(NOW).build();
        return new StateChangeEvent(null, sensor, Set.of(), Instant.now(), "test", trigger);
    }
}
