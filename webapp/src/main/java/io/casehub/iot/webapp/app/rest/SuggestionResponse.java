package io.casehub.iot.webapp.app.rest;

import io.casehub.iot.webapp.cbr.ResolutionSuggestion;

import java.util.List;
import java.util.UUID;

public record SuggestionResponse(
        UUID caseId,
        String caseType,
        int suggestionCount,
        List<ResolutionSuggestion> suggestions
) {
    public SuggestionResponse {
        suggestions = suggestions != null ? List.copyOf(suggestions) : List.of();
    }
}
