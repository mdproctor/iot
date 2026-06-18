package io.casehub.iot.bridge.agent;

import io.casehub.iot.api.StateChangeEvent;
import io.casehub.iot.api.bridge.BridgeMessage;
import io.casehub.iot.testing.Fixtures;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryBridgeEventStoreTest {

    private static final Instant NOW = Instant.now();

    private BridgeMessage event(String deviceId) {
        var device = Fixtures.hallwaySwitch().toBuilder()
                .deviceId(deviceId).build();
        var sce = new StateChangeEvent(null, device, Set.of("on"), NOW, "test");
        return new BridgeMessage.StateChange("t1", NOW, sce);
    }

    @Test
    void storeAndDrainInFifoOrder() {
        var store = new InMemoryBridgeEventStore(100);
        store.store(event("d1"));
        store.store(event("d2"));
        store.store(event("d3"));

        List<BridgeMessage> drained = store.drain();

        assertThat(drained).hasSize(3);
        assertThat(((BridgeMessage.StateChange) drained.get(0)).event().after().deviceId()).isEqualTo("d1");
        assertThat(((BridgeMessage.StateChange) drained.get(2)).event().after().deviceId()).isEqualTo("d3");
    }

    @Test
    void boundedEvictionDropsOldest() {
        var store = new InMemoryBridgeEventStore(2);
        store.store(event("d1"));
        store.store(event("d2"));
        store.store(event("d3"));

        List<BridgeMessage> drained = store.drain();

        assertThat(drained).hasSize(2);
        assertThat(((BridgeMessage.StateChange) drained.get(0)).event().after().deviceId()).isEqualTo("d2");
        assertThat(((BridgeMessage.StateChange) drained.get(1)).event().after().deviceId()).isEqualTo("d3");
    }

    @Test
    void drainClearsStore() {
        var store = new InMemoryBridgeEventStore(100);
        store.store(event("d1"));

        store.drain();

        assertThat(store.isEmpty()).isTrue();
    }

    @Test
    void emptyDrainReturnsEmptyList() {
        var store = new InMemoryBridgeEventStore(100);

        assertThat(store.drain()).isEmpty();
    }

    @Test
    void concurrentStoreIsThreadSafe() throws Exception {
        var store = new InMemoryBridgeEventStore(10000);
        int threadCount = 8;
        int eventsPerThread = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                for (int i = 0; i < eventsPerThread; i++) {
                    store.store(event("t" + threadId + "-d" + i));
                }
                latch.countDown();
            });
        }

        latch.await();
        executor.shutdown();

        List<BridgeMessage> drained = store.drain();
        assertThat(drained).hasSize(threadCount * eventsPerThread);
    }
}
