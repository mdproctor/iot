package io.casehub.iot.api.bridge;

import java.util.List;

public interface BridgeAuditStore {

    void save(BridgeAuditEvent event);

    /**
     * Returns events matching the query criteria, ordered by
     * {@code receivedAt} descending (newest first). Implementations
     * MUST honour this ordering contract.
     */
    List<BridgeAuditEvent> query(BridgeAuditQuery query);
}
