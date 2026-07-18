package io.casehub.iot.webapp.app.cbr;

import io.casehub.iot.webapp.cbr.WorkItemPredictionService;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
public class WorkItemPredictionServiceProducer {

    @Inject
    CbrCaseMemoryStore cbrStore;

    @Inject
    WorkItemCbrConfig config;

    @Produces
    @ApplicationScoped
    public WorkItemPredictionService predictionService() {
        return new WorkItemPredictionService(cbrStore, config.topK(), config.minSimilarity());
    }
}
