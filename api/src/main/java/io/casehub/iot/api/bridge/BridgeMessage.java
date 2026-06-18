package io.casehub.iot.api.bridge;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.casehub.iot.api.CommandResult;
import io.casehub.iot.api.DeviceCommand;
import io.casehub.iot.api.DeviceEntity;
import io.casehub.iot.api.ProviderStatusEvent;
import io.casehub.iot.api.StateChangeEvent;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Wire protocol for bridge communication. Each message carries a tenancy ID
 * and timestamp, with type-specific payload in the sealed variant.
 *
 * <p>Jackson polymorphic serialization uses {@code @type} as the discriminator.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = BridgeMessage.StateChange.class, name = "STATE_CHANGE"),
    @JsonSubTypes.Type(value = BridgeMessage.StateSnapshot.class, name = "STATE_SNAPSHOT"),
    @JsonSubTypes.Type(value = BridgeMessage.ProviderStatusChange.class, name = "PROVIDER_STATUS"),
    @JsonSubTypes.Type(value = BridgeMessage.Command.class, name = "COMMAND"),
    @JsonSubTypes.Type(value = BridgeMessage.CommandResponse.class, name = "COMMAND_RESULT"),
    @JsonSubTypes.Type(value = BridgeMessage.Heartbeat.class, name = "HEARTBEAT"),
    @JsonSubTypes.Type(value = BridgeMessage.ReplayedStateChange.class, name = "REPLAYED_STATE_CHANGE")
})
public sealed interface BridgeMessage {

    String tenancyId();

    Instant timestamp();

    record StateChange(String tenancyId, Instant timestamp, StateChangeEvent event)
            implements BridgeMessage {
        public StateChange {
            Objects.requireNonNull(tenancyId, "tenancyId");
            Objects.requireNonNull(timestamp, "timestamp");
            Objects.requireNonNull(event, "event");
        }
    }

    record StateSnapshot(String tenancyId, Instant timestamp, List<DeviceEntity> devices)
            implements BridgeMessage {
        public StateSnapshot {
            Objects.requireNonNull(tenancyId, "tenancyId");
            Objects.requireNonNull(timestamp, "timestamp");
            Objects.requireNonNull(devices, "devices");
            devices = List.copyOf(devices);
        }
    }

    record ProviderStatusChange(String tenancyId, Instant timestamp, ProviderStatusEvent status)
            implements BridgeMessage {
        public ProviderStatusChange {
            Objects.requireNonNull(tenancyId, "tenancyId");
            Objects.requireNonNull(timestamp, "timestamp");
            Objects.requireNonNull(status, "status");
        }
    }

    record Command(String tenancyId, Instant timestamp, String correlationId, DeviceCommand command)
            implements BridgeMessage {
        public Command {
            Objects.requireNonNull(tenancyId, "tenancyId");
            Objects.requireNonNull(timestamp, "timestamp");
            Objects.requireNonNull(correlationId, "correlationId");
            Objects.requireNonNull(command, "command");
        }
    }

    record CommandResponse(String tenancyId, Instant timestamp, String correlationId, CommandResult result)
            implements BridgeMessage {
        public CommandResponse {
            Objects.requireNonNull(tenancyId, "tenancyId");
            Objects.requireNonNull(timestamp, "timestamp");
            Objects.requireNonNull(correlationId, "correlationId");
            Objects.requireNonNull(result, "result");
        }
    }

    record Heartbeat(String tenancyId, Instant timestamp) implements BridgeMessage {
        public Heartbeat {
            Objects.requireNonNull(tenancyId, "tenancyId");
            Objects.requireNonNull(timestamp, "timestamp");
        }
    }

    record ReplayedStateChange(String tenancyId, Instant timestamp, StateChangeEvent event)
            implements BridgeMessage {
        public ReplayedStateChange {
            Objects.requireNonNull(tenancyId, "tenancyId");
            Objects.requireNonNull(timestamp, "timestamp");
            Objects.requireNonNull(event, "event");
        }
    }
}
