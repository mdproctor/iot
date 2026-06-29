package io.casehub.iot.bridge.persistence.memory;

import io.casehub.iot.api.bridge.BridgeAuditEvent;
import io.casehub.iot.api.bridge.BridgeAuditQuery;
import io.casehub.iot.api.bridge.BridgeAuditStore;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import java.util.ArrayDeque;
import java.util.List;

/**
 * Assumes events are saved in chronological order; does not re-sort
 * by {@code receivedAt}. Ordering relies on insertion order (addFirst).
 */
@Alternative
@Priority(100)
@ApplicationScoped
public class InMemoryBridgeAuditStore implements BridgeAuditStore {

    private final int maxSize;
    private final ArrayDeque<BridgeAuditEvent> events;

    @Inject
    public InMemoryBridgeAuditStore(final InMemoryAuditStoreConfig config) {
        this.maxSize = config.maxSize();
        this.events = new ArrayDeque<>(Math.min(maxSize, 1024));
    }

    InMemoryBridgeAuditStore(final int maxSize) {
        this.maxSize = maxSize;
        this.events = new ArrayDeque<>(Math.min(maxSize, 1024));
    }

    @Override
    public synchronized void save(final BridgeAuditEvent event) {
        if (events.size() >= maxSize) {
            events.removeLast();
        }
        events.addFirst(event);
    }

    @Override
    public List<BridgeAuditEvent> query(final BridgeAuditQuery query) {
        final List<BridgeAuditEvent> snapshot;
        synchronized (this) {
            snapshot = List.copyOf(events);
        }
        return snapshot.stream()
            .filter(e -> matches(e, query))
            .skip(query.offset())
            .limit(query.limit())
            .toList();
    }

    private static boolean matches(final BridgeAuditEvent event, final BridgeAuditQuery query) {
        if (query.tenancyId() != null && !query.tenancyId().equals(event.tenancyId())) return false;
        if (query.eventType() != null && query.eventType() != event.eventType()) return false;
        if (query.deviceId() != null && !query.deviceId().equals(event.deviceId())) return false;
        if (query.correlationId() != null && !query.correlationId().equals(event.correlationId())) return false;
        if (query.from() != null && event.receivedAt().isBefore(query.from())) return false;
        if (query.to() != null && event.receivedAt().isAfter(query.to())) return false;
        return true;
    }
}
