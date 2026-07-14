package io.casehub.iot.webapp.cbr;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResolutionConfidenceTest {

    @Test
    void emptyList_returnsNone() {
        var confidence = ResolutionConfidence.compute(List.of(), 0.85, 0.80);
        assertThat(confidence.level()).isEqualTo(ResolutionConfidence.ConfidenceLevel.NONE);
        assertThat(confidence.matchCount()).isZero();
        assertThat(confidence.bestSimilarity()).isEqualTo(0.0);
        assertThat(confidence.outcomeConsistency()).isEqualTo(0.0);
    }

    @Test
    void highConfidence_allConsistent() {
        var suggestions = List.of(
                suggestion(0.92, "RESOLVED"),
                suggestion(0.88, "RESOLVED"),
                suggestion(0.86, "RESOLVED"));
        var confidence = ResolutionConfidence.compute(suggestions, 0.85, 0.80);
        assertThat(confidence.level()).isEqualTo(ResolutionConfidence.ConfidenceLevel.HIGH);
        assertThat(confidence.bestSimilarity()).isEqualTo(0.92);
        assertThat(confidence.outcomeConsistency()).isEqualTo(1.0);
        assertThat(confidence.matchCount()).isEqualTo(3);
    }

    @Test
    void mediumConfidence_highSimilarityButMixedOutcomes() {
        var suggestions = List.of(
                suggestion(0.90, "RESOLVED"),
                suggestion(0.87, "FAILED"),
                suggestion(0.86, "RESOLVED"));
        var confidence = ResolutionConfidence.compute(suggestions, 0.85, 0.80);
        assertThat(confidence.level()).isEqualTo(ResolutionConfidence.ConfidenceLevel.MEDIUM);
        assertThat(confidence.outcomeConsistency())
                .isCloseTo(0.667, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void lowConfidence_belowSimilarityThreshold() {
        var suggestions = List.of(
                suggestion(0.40, "RESOLVED"),
                suggestion(0.35, "RESOLVED"));
        var confidence = ResolutionConfidence.compute(suggestions, 0.85, 0.80);
        assertThat(confidence.level()).isEqualTo(ResolutionConfidence.ConfidenceLevel.LOW);
    }

    @Test
    void mediumConfidence_midRangeSimilarity() {
        var suggestions = List.of(
                suggestion(0.60, "RESOLVED"),
                suggestion(0.55, "RESOLVED"));
        var confidence = ResolutionConfidence.compute(suggestions, 0.85, 0.80);
        assertThat(confidence.level()).isEqualTo(ResolutionConfidence.ConfidenceLevel.MEDIUM);
    }

    @Test
    void singleMatch_highSimilarity_isHigh() {
        var suggestions = List.of(suggestion(0.95, "RESOLVED"));
        var confidence = ResolutionConfidence.compute(suggestions, 0.85, 0.80);
        assertThat(confidence.level()).isEqualTo(ResolutionConfidence.ConfidenceLevel.HIGH);
        assertThat(confidence.outcomeConsistency()).isEqualTo(1.0);
    }

    @Test
    void nullOutcomes_treatedAsDistinct() {
        var suggestions = List.of(
                suggestion(0.90, null),
                suggestion(0.88, "RESOLVED"),
                suggestion(0.86, "RESOLVED"));
        var confidence = ResolutionConfidence.compute(suggestions, 0.85, 0.80);
        assertThat(confidence.outcomeConsistency())
                .isCloseTo(0.667, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void fiveMatches_threeResolved_onePartial_oneFailed() {
        var suggestions = List.of(
                suggestion(0.90, "RESOLVED"),
                suggestion(0.88, "RESOLVED"),
                suggestion(0.87, "RESOLVED_PARTIAL"),
                suggestion(0.86, "RESOLVED"),
                suggestion(0.85, "FAILED"));
        var confidence = ResolutionConfidence.compute(suggestions, 0.85, 0.80);
        assertThat(confidence.outcomeConsistency()).isEqualTo(0.6);
        assertThat(confidence.level()).isEqualTo(ResolutionConfidence.ConfidenceLevel.MEDIUM);
    }

    @Test
    void exactlyAtThresholds_isHigh() {
        var suggestions = List.of(
                suggestion(0.85, "RESOLVED"),
                suggestion(0.80, "RESOLVED"),
                suggestion(0.75, "RESOLVED"),
                suggestion(0.70, "RESOLVED"),
                suggestion(0.65, "FAILED"));
        var confidence = ResolutionConfidence.compute(suggestions, 0.85, 0.80);
        assertThat(confidence.bestSimilarity()).isEqualTo(0.85);
        assertThat(confidence.outcomeConsistency()).isEqualTo(0.8);
        assertThat(confidence.level()).isEqualTo(ResolutionConfidence.ConfidenceLevel.HIGH);
    }

    @Test
    void justBelowConsistencyThreshold_isMedium() {
        var suggestions = List.of(
                suggestion(0.90, "RESOLVED"),
                suggestion(0.88, "RESOLVED"),
                suggestion(0.86, "FAILED"),
                suggestion(0.85, "FAILED"));
        var confidence = ResolutionConfidence.compute(suggestions, 0.85, 0.80);
        assertThat(confidence.outcomeConsistency()).isEqualTo(0.5);
        assertThat(confidence.level()).isEqualTo(ResolutionConfidence.ConfidenceLevel.MEDIUM);
    }

    private static ResolutionSuggestion suggestion(double score, String outcome) {
        return new ResolutionSuggestion(
                "case-id", score, "problem", "solution", outcome, null,
                Map.of(), Map.of(), List.of());
    }
}
