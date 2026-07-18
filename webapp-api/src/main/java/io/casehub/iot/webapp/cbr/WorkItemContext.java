package io.casehub.iot.webapp.cbr;

import java.time.Instant;
import java.util.List;

public record WorkItemContext(
        String workItemTitle,
        String workItemDescription,
        List<String> workItemTypes,
        String priority,
        String candidateGroups,
        String workerName,
        String caseTypeName,
        String deviceClass,
        String roomType,
        Instant eventTimestamp,
        String terminalStatus,
        String resolvedBy,
        Instant createdAt,
        Instant completedAt
) {}
