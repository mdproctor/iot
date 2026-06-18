package io.casehub.iot.bridge.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.iot.api.StateChangeEvent;
import io.casehub.iot.api.bridge.BridgeMessage;
import io.casehub.iot.testing.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PersistentBridgeEventStoreTest {

    private static final Instant NOW = Instant.parse("2026-06-18T00:00:00Z");
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @TempDir
    Path tempDir;

    private PersistentBridgeEventStore store;

    @BeforeEach
    void setUp() {
        store = new PersistentBridgeEventStore(tempDir.toString(), MAPPER);
    }

    private BridgeMessage event(String deviceId) {
        var device = Fixtures.hallwaySwitch().toBuilder()
                .deviceId(deviceId).build();
        var sce = new StateChangeEvent(null, device, Set.of("on"), NOW, "test");
        return new BridgeMessage.StateChange("t1", NOW, sce);
    }

    @Test
    void storeAndDrainInFifoOrder() {
        store.store(event("d1"));
        store.store(event("d2"));
        store.store(event("d3"));

        List<BridgeMessage> drained = store.drain();

        assertThat(drained).hasSize(3);
        assertThat(((BridgeMessage.StateChange) drained.get(0)).event().after().deviceId()).isEqualTo("d1");
        assertThat(((BridgeMessage.StateChange) drained.get(2)).event().after().deviceId()).isEqualTo("d3");
    }

    @Test
    void drainDeletesFile() {
        store.store(event("d1"));
        store.drain();

        assertThat(store.isEmpty()).isTrue();
        assertThat(Files.exists(tempDir.resolve("events.ndjson"))).isFalse();
    }

    @Test
    void startupWithExistingFileReplays() throws IOException {
        store.store(event("d1"));
        store.store(event("d2"));

        var store2 = new PersistentBridgeEventStore(tempDir.toString(), MAPPER);
        List<BridgeMessage> drained = store2.drain();

        assertThat(drained).hasSize(2);
    }

    @Test
    void emptyDrainWithNoFile() {
        assertThat(store.drain()).isEmpty();
        assertThat(store.isEmpty()).isTrue();
    }

    @Test
    void concurrentStoreAndDrain() throws Exception {
        int count = 50;
        var latch = new java.util.concurrent.CountDownLatch(2);

        Thread writer = new Thread(() -> {
            for (int i = 0; i < count; i++) {
                store.store(event("w-" + i));
            }
            latch.countDown();
        });

        Thread drainer = new Thread(() -> {
            try { Thread.sleep(10); } catch (InterruptedException e) { }
            store.drain();
            latch.countDown();
        });

        writer.start();
        drainer.start();
        latch.await();
    }

    @Test
    void corruptLineSkippedWithWarning() throws IOException {
        Path file = tempDir.resolve("events.ndjson");
        String valid1 = MAPPER.writeValueAsString(event("d1"));
        String valid2 = MAPPER.writeValueAsString(event("d2"));
        Files.writeString(file, valid1 + "\n{CORRUPT JSON\n" + valid2 + "\n");

        List<BridgeMessage> drained = store.drain();

        assertThat(drained).hasSize(2);
        assertThat(((BridgeMessage.StateChange) drained.get(0)).event().after().deviceId()).isEqualTo("d1");
        assertThat(((BridgeMessage.StateChange) drained.get(1)).event().after().deviceId()).isEqualTo("d2");
    }
}
