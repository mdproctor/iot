package io.casehub.iot.webapp.app.cbr;

import io.casehub.iot.webapp.cbr.IoTCbrRetrievalService;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
public class IoTCbrRetrievalServiceProducer {

    @Inject
    CbrCaseMemoryStore cbrStore;

    @Produces
    @ApplicationScoped
    public IoTCbrRetrievalService retrievalService() {
        return new IoTCbrRetrievalService(cbrStore);
    }
}
