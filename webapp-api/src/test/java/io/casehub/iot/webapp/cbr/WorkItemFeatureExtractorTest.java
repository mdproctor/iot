package io.casehub.iot.webapp.cbr;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkItemFeatureExtractorTest {

    @Test
    void extractForRetrieve_allFieldsPopulated_returnsInputFeaturesOnly() {
        var ctx = new WorkItemContext(
                "Inspect sensor", "Temperature sensor offline", List.of("human-review"),
                "HIGH", "hvac-technicians", "human-review", "hvac-anomaly",
                "thermostat", "bedroom", Instant.parse("2026-07-17T14:30:00Z"),
                "COMPLETED", "tech-1", Instant.parse("2026-07-17T12:00:00Z"),
                Instant.parse("2026-07-17T14:30:00Z"));

        Map<String, Object> features = WorkItemFeatureExtractor.extractForRetrieve(ctx);

        assertThat(features).containsEntry("caseType", "hvac-anomaly");
        assertThat(features).containsEntry("workerName", "human-review");
        assertThat(features).containsEntry("deviceClass", "thermostat");
        assertThat(features).containsEntry("roomType", "bedroom");
        assertThat(features).containsEntry("priority", "HIGH");
        assertThat(features).containsEntry("candidateGroups", "hvac-technicians");
        assertThat(features).containsKey("hourOfDay");
        assertThat(features).containsKey("dayType");
        assertThat(features).containsKey("season");
        assertThat(features).doesNotContainKey("resolutionDurationMinutes");
        assertThat(features).doesNotContainKey("resolvedBy");
        assertThat(features).doesNotContainKey("terminalStatus");
    }

    @Test
    void extractForRetain_includesOutputFeatures() {
        var ctx = new WorkItemContext(
                "Inspect sensor", "Sensor offline", List.of("human-review"),
                "HIGH", "hvac-technicians", "human-review", "hvac-anomaly",
                "thermostat", "bedroom", Instant.parse("2026-07-17T14:30:00Z"),
                "COMPLETED", "tech-1",
                Instant.parse("2026-07-17T12:00:00Z"),
                Instant.parse("2026-07-17T14:30:00Z"));

        Map<String, Object> features = WorkItemFeatureExtractor.extractForRetain(ctx);

        assertThat(features).containsEntry("terminalStatus", "COMPLETED");
        assertThat(features).containsEntry("resolvedBy", "tech-1");
        assertThat(features).containsEntry("resolutionDurationMinutes", 150.0);
    }

    @Test
    void extractForRetrieve_nullableFieldsAbsent_omittedFromMap() {
        var ctx = new WorkItemContext(
                "Review alert", "Generic alert", List.of("human-review"),
                "MEDIUM", "ops-team", "human-review", "generic-response",
                null, null, null,
                null, null, null, null);

        Map<String, Object> features = WorkItemFeatureExtractor.extractForRetrieve(ctx);

        assertThat(features).containsEntry("caseType", "generic-response");
        assertThat(features).containsEntry("priority", "MEDIUM");
        assertThat(features).doesNotContainKey("deviceClass");
        assertThat(features).doesNotContainKey("roomType");
        assertThat(features).doesNotContainKey("hourOfDay");
        assertThat(features).doesNotContainKey("dayType");
        assertThat(features).doesNotContainKey("season");
    }

    @Test
    void extractForRetain_nullOutputFields_omittedFromMap() {
        var ctx = new WorkItemContext(
                "Review", "Alert", List.of("human-review"),
                "LOW", "ops", "human-review", "generic-response",
                null, null, null,
                null, null, null, null);

        Map<String, Object> features = WorkItemFeatureExtractor.extractForRetain(ctx);

        assertThat(features).doesNotContainKey("terminalStatus");
        assertThat(features).doesNotContainKey("resolvedBy");
        assertThat(features).doesNotContainKey("resolutionDurationMinutes");
    }

    @Test
    void temporalDerivation_weekday() {
        var ctx = new WorkItemContext(
                "T", "D", List.of(), "HIGH", "g", "w", "c",
                null, null, Instant.parse("2026-07-15T09:00:00Z"),
                null, null, null, null);

        Map<String, Object> features = WorkItemFeatureExtractor.extractForRetrieve(ctx);

        assertThat(features).containsEntry("hourOfDay", 9.0);
        assertThat(features).containsEntry("dayType", "weekday");
        assertThat(features).containsEntry("season", "summer");
    }

    @Test
    void temporalDerivation_weekend() {
        var ctx = new WorkItemContext(
                "T", "D", List.of(), "HIGH", "g", "w", "c",
                null, null, Instant.parse("2026-07-18T22:00:00Z"),
                null, null, null, null);

        Map<String, Object> features = WorkItemFeatureExtractor.extractForRetrieve(ctx);

        assertThat(features).containsEntry("hourOfDay", 22.0);
        assertThat(features).containsEntry("dayType", "weekend");
    }

    @Test
    void temporalDerivation_winterSeason() {
        var ctx = new WorkItemContext(
                "T", "D", List.of(), "HIGH", "g", "w", "c",
                null, null, Instant.parse("2026-01-15T10:00:00Z"),
                null, null, null, null);

        Map<String, Object> features = WorkItemFeatureExtractor.extractForRetrieve(ctx);

        assertThat(features).containsEntry("season", "winter");
    }
}
