package io.casehub.iot.webapp.drools;

import io.casehub.ras.drools.DroolsSessionKey;
import io.casehub.ras.drools.InMemoryDroolsSessionStore;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

class TestDroolsSessionStore extends InMemoryDroolsSessionStore {

    private final ConcurrentHashMap<DroolsSessionKey, KieSession> sessions = new ConcurrentHashMap<>();

    @Override
    public KieSession computeIfAbsent(DroolsSessionKey key,
                                      KieBase kieBase,
                                      KieSessionConfiguration config,
                                      long generation) {
        KieSession session = super.computeIfAbsent(key, kieBase, config, generation);
        sessions.put(key, session);
        return session;
    }

    @Override
    public void remove(DroolsSessionKey key) {
        super.remove(key);
        sessions.remove(key);
    }

    @Override
    public void removeAll(String ganglionId) {
        super.removeAll(ganglionId);
        sessions.entrySet().removeIf(e -> e.getKey().ganglionId().equals(ganglionId));
    }

    Optional<KieSession> get(String ganglionId, String situationId,
                             String correlationKey, String tenancyId) {
        return Optional.ofNullable(
                sessions.get(new DroolsSessionKey(ganglionId, situationId, correlationKey, tenancyId)));
    }
}
