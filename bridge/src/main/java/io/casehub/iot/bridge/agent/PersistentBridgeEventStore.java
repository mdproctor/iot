package io.casehub.iot.bridge.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.iot.api.bridge.BridgeMessage;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

@Alternative
@Priority(1)
@ApplicationScoped
public class PersistentBridgeEventStore implements BridgeEventStore {

    private static final Logger LOG = Logger.getLogger(PersistentBridgeEventStore.class);
    private static final String FILE_NAME = "events.ndjson";

    private final Path filePath;
    private final ObjectMapper mapper;

    @Inject
    public PersistentBridgeEventStore(BridgeAgentConfig config, ObjectMapper mapper) {
        this.filePath = Path.of(config.eventStore().directory()).resolve(FILE_NAME);
        this.mapper = mapper;
    }

    PersistentBridgeEventStore(String directory, ObjectMapper mapper) {
        this.filePath = Path.of(directory).resolve(FILE_NAME);
        this.mapper = mapper;
    }

    @Override
    public synchronized void store(BridgeMessage message) {
        try {
            Files.createDirectories(filePath.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(filePath,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(mapper.writeValueAsString(message));
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            LOG.errorf(e, "Failed to persist event to %s", filePath);
        }
    }

    @Override
    public synchronized List<BridgeMessage> drain() {
        if (!Files.exists(filePath)) {
            return List.of();
        }
        List<BridgeMessage> events = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(filePath);
            for (String line : lines) {
                if (line.isBlank()) continue;
                try {
                    events.add(mapper.readValue(line, BridgeMessage.class));
                } catch (Exception e) {
                    LOG.warnf("Skipping corrupt NDJSON line: %s", line.length() > 100 ? line.substring(0, 100) + "..." : line);
                }
            }
            Files.delete(filePath);
        } catch (IOException e) {
            LOG.errorf(e, "Failed to drain events from %s", filePath);
        }
        return List.copyOf(events);
    }

    @Override
    public synchronized boolean isEmpty() {
        if (!Files.exists(filePath)) return true;
        try {
            return Files.size(filePath) == 0;
        } catch (IOException e) {
            return true;
        }
    }
}
