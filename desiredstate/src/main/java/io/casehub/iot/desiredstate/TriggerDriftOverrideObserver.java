package io.casehub.iot.desiredstate;

import io.casehub.desiredstate.api.Exemption;
import io.casehub.desiredstate.api.ExemptionSpec;
import io.casehub.desiredstate.api.ExemptionStore;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.RevertCondition;
import io.casehub.iot.api.DeviceClass;
import io.casehub.iot.api.DeviceEntity;
import io.casehub.iot.api.StateChangeEvent;
import io.casehub.iot.api.TriggerSource;
import io.casehub.iot.api.spi.DeviceRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class TriggerDriftOverrideObserver {

    private final ExemptionStore exemptionStore;
    private final ActivePresetRegistry presetRegistry;
    private final DeviceRegistry deviceRegistry;
    private final IoTDriftPolicy driftPolicy;

    @Inject
    public TriggerDriftOverrideObserver(ExemptionStore exemptionStore,
                                        ActivePresetRegistry presetRegistry,
                                        DeviceRegistry deviceRegistry,
                                        IoTDriftPolicy driftPolicy) {
        this.exemptionStore = exemptionStore;
        this.presetRegistry = presetRegistry;
        this.deviceRegistry = deviceRegistry;
        this.driftPolicy = driftPolicy;
    }

    void onStateChange(@ObservesAsync StateChangeEvent event) {
        TriggerSource trigger = event.triggerSource();
        if (trigger == TriggerSource.UNKNOWN) return;

        String tenancyId = event.after().tenancyId();
        Optional<ActivePresetRegistry.ActivePreset> active = presetRegistry.get(tenancyId);
        if (active.isEmpty()) return;

        String sourceDeviceId = event.after().deviceId();

        List<OverrideRule> matchingRules = active.get().overrideRules().stream()
                .filter(r -> r.trigger() == trigger)
                .filter(r -> r.source() == null || r.source().equals(sourceDeviceId))
                .toList();

        for (OverrideRule rule : matchingRules) {
            List<DeviceEntity> targets = resolveTargets(rule.targets(), tenancyId);
            for (DeviceEntity target : targets) {
                if (driftPolicy.isHardConstrained(target.deviceClass())) continue;

                NodeId nodeId = NodeId.of(target.deviceId() + "-config");
                RevertCondition condition = rule.revert().toRevertCondition();
                ExemptionSpec spec = new ExemptionSpec(condition, Map.of(
                        "trigger", trigger.name(),
                        "source", sourceDeviceId,
                        "preset", active.get().name()));
                Instant now = Instant.now();
                Instant expiresAt = computeExpiry(condition, now);
                exemptionStore.grant(tenancyId, nodeId,
                        new Exemption(nodeId, spec, now, expiresAt));
            }
        }
    }

    List<DeviceEntity> resolveTargets(String targets, String tenancyId) {
        if (!targets.contains("/")) {
            return deviceRegistry.findById(targets)
                    .filter(d -> d.tenancyId().equals(tenancyId))
                    .map(List::of)
                    .orElse(List.of());
        }
        int lastSlash = targets.lastIndexOf('/');
        String locationPrefix = targets.substring(0, lastSlash);
        String classQualifier = targets.substring(lastSlash + 1);

        DeviceClass targetClass;
        try {
            targetClass = DeviceClass.valueOf(classQualifier.toUpperCase());
        } catch (IllegalArgumentException e) {
            return deviceRegistry.findAll().stream()
                    .filter(d -> d.tenancyId().equals(tenancyId))
                    .filter(d -> d.location() != null && d.location().startsWith(targets))
                    .toList();
        }

        return deviceRegistry.findAll().stream()
                .filter(d -> d.tenancyId().equals(tenancyId))
                .filter(d -> d.deviceClass() == targetClass)
                .filter(d -> d.location() != null && d.location().startsWith(locationPrefix))
                .toList();
    }

    private Instant computeExpiry(RevertCondition condition, Instant now) {
        if (condition instanceof RevertCondition.OnDuration d) {
            return now.plus(d.duration());
        }
        return null;
    }
}
