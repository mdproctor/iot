package io.casehub.iot.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

import java.io.UncheckedIOException;
import java.net.URI;
import java.time.ZoneOffset;
import java.util.UUID;

@ApplicationScoped
public class IoTCloudEventAdapter {

    private static final URI SOURCE = URI.create("/casehub-iot");
    private static final String TYPE_PREFIX = "io.casehub.iot.state_change.";
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Event<CloudEvent> cloudEvents;

    @Inject
    public IoTCloudEventAdapter(Event<CloudEvent> cloudEvents) {
        this.cloudEvents = cloudEvents;
    }

    void onStateChange(@ObservesAsync StateChangeEvent event) {
        String deviceClass = event.after().deviceClass().name().toLowerCase();
        byte[] data;
        try {
            data = MAPPER.writeValueAsBytes(event);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        CloudEvent ce = CloudEventBuilder.v1()
            .withId(UUID.randomUUID().toString())
            .withType(TYPE_PREFIX + deviceClass)
            .withSource(SOURCE)
            .withSubject("device/" + event.after().deviceId())
            .withTime(event.occurredAt().atOffset(ZoneOffset.UTC))
            .withData("application/json", data)
            .withExtension("tenancyid", event.after().tenancyId())
            .withExtension("providerid", event.providerId())
            .build();

        cloudEvents.fireAsync(ce);
    }
}
