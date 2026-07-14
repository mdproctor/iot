package io.casehub.iot.webapp.app.rest;

import io.casehub.api.context.CaseContext;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.cbr.CbrConfig;
import io.casehub.api.model.cbr.FeatureExtractor;
import io.casehub.api.model.cbr.LambdaFeatureExtractor;
import io.casehub.engine.common.internal.model.CaseInstance;
import io.casehub.engine.common.spi.CaseDefinitionRegistry;
import io.casehub.engine.common.spi.cache.CaseInstanceCache;
import io.casehub.iot.webapp.cbr.IoTCbrRetrievalService;
import io.casehub.iot.webapp.cbr.ResolutionSuggestion;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST resource for case instance queries.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/cases} — open cases triggered by situations
 *   <li>{@code GET /api/cases/{caseId}} — case detail with event log and worker results
 * </ul>
 *
 * <p>All queries filter by {@link CurrentPrincipal#tenancyId()}.
 *
 * <p>TODO: Integrate with casehub-engine's {@code CaseInstanceRepository} or equivalent
 * API for querying case instances. Current implementation is a placeholder until
 * engine APIs are available.
 */
@Path("/api/cases")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CaseResource {

    @Inject
    CurrentPrincipal principal;
    @Inject
    CaseInstanceCache caseInstanceCache;
    @Inject
    CaseDefinitionRegistry caseDefinitionRegistry;
    @Inject
    IoTCbrRetrievalService retrievalService;


    /**
     * List open cases with optional filters.
     *
     * @param status      filter by case status (e.g., "OPEN", "COMPLETED")
     * @param situationId filter by source situation ID
     * @param from        start time
     * @param to          end time
     * @return list of case summaries
     */
    @GET
    @RolesAllowed("iot-viewer")
    public List<CaseSummaryResponse> list(
            @QueryParam("status") String status,
            @QueryParam("situationId") String situationId,
            @QueryParam("from") Instant from,
            @QueryParam("to") Instant to
    ) {
        // TODO: Query engine's CaseInstanceRepository
        // For now, return empty list until engine integration is complete
        return List.of();
    }

    /**
     * Get case detail with event log and worker results.
     *
     * @param caseId case instance ID
     * @return case detail
     */
    @GET
    @Path("/{caseId}")
    @RolesAllowed("iot-viewer")
    public CaseDetailResponse get(@PathParam("caseId") UUID caseId) {
        // TODO: Query engine for case instance + event log + worker outcomes
        // Check tenancy via principal.tenancyId()
        throw new NotFoundException("Case not found: " + caseId);
    }

    @GET
    @Path("/{caseId}/suggestions")
    @RolesAllowed("iot-viewer")
    public SuggestionResponse getSuggestions(@PathParam("caseId") UUID caseId) {
        CaseInstance instance = caseInstanceCache.get(caseId);
        if (instance == null) {
            throw new NotFoundException("Case not found: " + caseId);
        }

        String caseType = instance.getCaseMetaModel().getName();

        Optional<CaseDefinition> defOpt = caseDefinitionRegistry.findByName(caseType);
        if (defOpt.isEmpty()) {
            return new SuggestionResponse(caseId, caseType, 0, List.of());
        }

        CbrConfig cbrConfig = defOpt.get().getCbrConfig();
        if (cbrConfig == null) {
            return new SuggestionResponse(caseId, caseType, 0, List.of());
        }

        Map<String, Object> features = extractFeatures(cbrConfig, instance.getCaseContext());
        List<ResolutionSuggestion> suggestions = retrievalService.retrieve(
                cbrConfig, features, principal.tenancyId());

        return new SuggestionResponse(caseId, caseType, suggestions.size(), suggestions);
    }

    @POST
    @Path("/{caseId}/suggestions/{pastCaseId}/accept")
    @RolesAllowed("iot-operator")
    public void acceptSuggestion(
            @PathParam("caseId") UUID caseId,
            @PathParam("pastCaseId") String pastCaseId) {

        CaseInstance instance = caseInstanceCache.get(caseId);
        if (instance == null) {
            throw new NotFoundException("Case not found: " + caseId);
        }

        var context = instance.getCaseContext();
        @SuppressWarnings("unchecked")
        var accepted = (java.util.Set<String>) context.getOrDefault(
                "acceptedSuggestions", new java.util.HashSet<String>());

        if (accepted.contains(pastCaseId)) {
            return;
        }

        String caseType = instance.getCaseMetaModel().getName();
        var    defOpt   = caseDefinitionRegistry.findByName(caseType);
        if (defOpt.isEmpty()) {
            throw new NotFoundException("Case definition not found: " + caseType);
        }

        CbrConfig cbrConfig = defOpt.get().getCbrConfig();
        if (cbrConfig == null) {
            throw new NotFoundException("No CBR config for case type: " + caseType);
        }

        var features    = extractFeatures(cbrConfig, context);
        var suggestions = retrievalService.retrieve(cbrConfig, features, principal.tenancyId());
        var match = suggestions.stream()
                               .filter(s -> pastCaseId.equals(s.caseId()))
                               .findFirst()
                               .orElseThrow(() -> new NotFoundException("Suggestion not found: " + pastCaseId));

        var planSteps = match.planSteps().stream()
                             .map(pt -> Map.<String, Object>of(
                                     "description", pt.capabilityName() + " via " + pt.workerName(),
                                     "actionType", pt.capabilityName(),
                                     "parameters", pt.parameters(),
                                     "priority", pt.priority(),
                                     "source", "cbr:" + pastCaseId))
                             .toList();

        context.set("suggestedPlan", planSteps);

        var newAccepted = new java.util.HashSet<>(accepted);
        newAccepted.add(pastCaseId);
        context.set("acceptedSuggestions", newAccepted);
    }

    private Map<String, Object> extractFeatures(CbrConfig config, CaseContext context) {
        FeatureExtractor extractor = config.featureExtractor();
        if (extractor instanceof LambdaFeatureExtractor lambda) {
            return lambda.extract(context);
        }
        return Map.of();
    }


    public record CaseSummaryResponse(
            UUID caseId,
            String caseType,
            String status,
            String situationId,
            int pendingActionsCount,
            Instant createdAt
    ) {
    }

    public record CaseDetailResponse(
            UUID caseId,
            String caseType,
            String status,
            String situationId,
            List<CaseEvent> eventLog,
            List<WorkerResult> workerResults,
            List<PlannedAction> plannedActions,
            Instant createdAt,
            Instant completedAt
    ) {
    }

    public record CaseEvent(
            Instant timestamp,
            String eventType,
            Object payload
    ) {
    }

    public record WorkerResult(
            String workerId,
            String outcome,
            Object data,
            Instant executedAt
    ) {
    }

    public record PlannedAction(
            String actionId,
            String description,
            String status,
            Instant scheduledAt
    ) {
    }
}
