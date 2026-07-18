package io.casehub.iot.webapp.cbr;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WorkItemFeatureExtractor {

    private WorkItemFeatureExtractor() {}

    public static Map<String, Object> extractForRetain(WorkItemContext ctx) {
        var features = extractInputFeatures(ctx);
        if (ctx.terminalStatus() != null) {
            features.put("terminalStatus", ctx.terminalStatus());
        }
        if (ctx.resolvedBy() != null) {
            features.put("resolvedBy", ctx.resolvedBy());
        }
        if (ctx.createdAt() != null && ctx.completedAt() != null) {
            long minutes = Duration.between(ctx.createdAt(), ctx.completedAt()).toMinutes();
            features.put("resolutionDurationMinutes", (double) minutes);
        }
        return Map.copyOf(features);
    }

    public static Map<String, Object> extractForRetrieve(WorkItemContext ctx) {
        return Map.copyOf(extractInputFeatures(ctx));
    }

    private static Map<String, Object> extractInputFeatures(WorkItemContext ctx) {
        var features = new LinkedHashMap<String, Object>();
        features.put("caseType", ctx.caseTypeName());
        features.put("workerName", ctx.workerName());
        if (ctx.deviceClass() != null) features.put("deviceClass", ctx.deviceClass());
        if (ctx.roomType() != null) features.put("roomType", ctx.roomType());
        features.put("priority", ctx.priority());
        features.put("candidateGroups", ctx.candidateGroups());
        IoTCbrFeatureExtractors.deriveTemporalFeatures(features, ctx.eventTimestamp());
        return features;
    }
}
