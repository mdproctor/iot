package io.casehub.iot.bridge.agent;

import io.casehub.iot.api.bridge.BridgeMessage;
import java.util.List;

public interface BridgeEventStore {
    void store(BridgeMessage message);
    List<BridgeMessage> drain();
    boolean isEmpty();
}
