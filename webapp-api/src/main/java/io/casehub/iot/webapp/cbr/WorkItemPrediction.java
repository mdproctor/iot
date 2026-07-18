package io.casehub.iot.webapp.cbr;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public record WorkItemPrediction(
        Map<String, Double> outcomeDistribution,
        Duration resolutionTimeP50,
        Duration resolutionTimeP90,
        List<AssigneeSuggestion> suggestedAssignees,
        double confidence,
        int sampleSize
) {
    public record AssigneeSuggestion(
            String assigneeId,
            double successRate,
            Duration avgResolutionTime,
            int taskCount
    ) {}

    public static WorkItemPrediction empty() {
        return new WorkItemPrediction(Map.of(), null, null, List.of(), 0.0, 0);
    }
}
