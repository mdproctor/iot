package io.casehub.iot.webapp.cbr;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public record ResolutionConfidence(
        double bestSimilarity,
        double outcomeConsistency,
        int matchCount,
        ConfidenceLevel level
) {
    public enum ConfidenceLevel { HIGH, MEDIUM, LOW, NONE }

    public static ResolutionConfidence compute(
            List<ResolutionSuggestion> suggestions,
            double minSimilarityForHigh,
            double minConsistencyForHigh) {

        if (suggestions.isEmpty()) {
            return new ResolutionConfidence(0.0, 0.0, 0, ConfidenceLevel.NONE);
        }

        double best = suggestions.stream()
                .mapToDouble(ResolutionSuggestion::similarityScore)
                .max().orElse(0.0);

        double consistency = computeOutcomeConsistency(suggestions);
        int count = suggestions.size();

        ConfidenceLevel level;
        if (best >= minSimilarityForHigh && consistency >= minConsistencyForHigh) {
            level = ConfidenceLevel.HIGH;
        } else if (best >= 0.5) {
            level = ConfidenceLevel.MEDIUM;
        } else {
            level = ConfidenceLevel.LOW;
        }

        return new ResolutionConfidence(best, consistency, count, level);
    }

    private static double computeOutcomeConsistency(List<ResolutionSuggestion> suggestions) {
        Map<String, Long> counts = suggestions.stream()
                .map(s -> s.outcome() != null ? s.outcome() : "__null__")
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        long mostFrequent = counts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        return (double) mostFrequent / suggestions.size();
    }
}
