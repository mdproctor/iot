package io.casehub.iot.api.bridge;

import io.casehub.iot.api.StateChangeEvent;
import io.smallrye.mutiny.Uni;

/**
 * Filter applied to state-change events before they are forwarded across
 * the bridge. Filters run in priority order (lower runs first).
 */
public interface BridgeEventFilter {

    /**
     * Priority of this filter. Lower values run first.
     */
    int priority();

    /**
     * Evaluate whether an event should be forwarded or suppressed.
     */
    Uni<FilterAction> filter(StateChangeEvent event, FilterContext ctx);
}
