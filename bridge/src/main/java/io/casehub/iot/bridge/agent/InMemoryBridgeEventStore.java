package io.casehub.iot.bridge.agent;

import io.casehub.iot.api.bridge.BridgeMessage;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayDeque;
import java.util.List;

@DefaultBean
@ApplicationScoped
public class InMemoryBridgeEventStore implements BridgeEventStore {

    private final int maxSize;
    private final ArrayDeque<BridgeMessage> buffer;

    @Inject
    public InMemoryBridgeEventStore(BridgeAgentConfig config) {
        this.maxSize = config.eventStore().maxSize();
        this.buffer = new ArrayDeque<>(Math.min(maxSize, 1024));
    }

    InMemoryBridgeEventStore(int maxSize) {
        this.maxSize = maxSize;
        this.buffer = new ArrayDeque<>(Math.min(maxSize, 1024));
    }

    @Override
    public synchronized void store(BridgeMessage message) {
        if (buffer.size() >= maxSize) {
            buffer.pollFirst();
        }
        buffer.addLast(message);
    }

    @Override
    public synchronized List<BridgeMessage> drain() {
        List<BridgeMessage> result = List.copyOf(buffer);
        buffer.clear();
        return result;
    }

    @Override
    public synchronized boolean isEmpty() {
        return buffer.isEmpty();
    }
}
