package io.casehub.iot.bridge.server.audit;

import io.casehub.iot.api.bridge.BridgeAuditEvent;
import io.casehub.iot.api.bridge.BridgeAuditEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingBridgeAuditObserverTest {

    private LoggingBridgeAuditObserver observer;
    private TestLogHandler logHandler;

    @BeforeEach
    void setUp() {
        observer = new LoggingBridgeAuditObserver();
        logHandler = new TestLogHandler();
        Logger logger = Logger.getLogger("io.casehub.iot.bridge.audit");
        logger.addHandler(logHandler);
    }

    @AfterEach
    void tearDown() {
        Logger logger = Logger.getLogger("io.casehub.iot.bridge.audit");
        logger.removeHandler(logHandler);
    }

    @Test
    void logsAgentConnectedEvent() {
        BridgeAuditEvent event = new BridgeAuditEvent(
                "site-a", Instant.now(), BridgeAuditEventType.AGENT_CONNECTED,
                null, null, null);

        observer.onAudit(event);

        assertThat(logHandler.messages).hasSize(1);
        String logged = logHandler.messages.get(0);
        assertThat(logged).contains("[AUDIT]");
        assertThat(logged).contains("type=AGENT_CONNECTED");
        assertThat(logged).contains("tenancy=site-a");
    }

    @Test
    void logsStateChangeEvent() {
        BridgeAuditEvent event = new BridgeAuditEvent(
                "site-b", Instant.now(), BridgeAuditEventType.STATE_CHANGE,
                null, "site-b/switch-1", null);

        observer.onAudit(event);

        assertThat(logHandler.messages).hasSize(1);
        String logged = logHandler.messages.get(0);
        assertThat(logged).contains("type=STATE_CHANGE");
        assertThat(logged).contains("tenancy=site-b");
        assertThat(logged).contains("device=site-b/switch-1");
    }

    @Test
    void logsCommandResponseWithCorrelationId() {
        BridgeAuditEvent event = new BridgeAuditEvent(
                "site-c", Instant.now(), BridgeAuditEventType.COMMAND_RESPONSE,
                "corr-123", "site-c/light-1", null);

        observer.onAudit(event);

        assertThat(logHandler.messages).hasSize(1);
        String logged = logHandler.messages.get(0);
        assertThat(logged).contains("type=COMMAND_RESPONSE");
        assertThat(logged).contains("correlation=corr-123");
        assertThat(logged).contains("device=site-c/light-1");
    }

    @Test
    void handlesNullFieldsGracefully() {
        BridgeAuditEvent event = new BridgeAuditEvent(
                "site-a", Instant.now(), BridgeAuditEventType.AGENT_DISCONNECTED,
                null, null, null);

        observer.onAudit(event);

        assertThat(logHandler.messages).hasSize(1);
        assertThat(logHandler.messages.get(0)).contains("type=AGENT_DISCONNECTED");
    }

    private static class TestLogHandler extends Handler {
        final List<String> messages = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            messages.add(record.getMessage());
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    }
}
