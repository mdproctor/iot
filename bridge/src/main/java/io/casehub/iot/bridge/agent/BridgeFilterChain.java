package io.casehub.iot.bridge.agent;

import io.casehub.iot.api.StateChangeEvent;
import io.casehub.iot.api.bridge.BridgeEventFilter;
import io.casehub.iot.api.bridge.FilterAction;
import io.casehub.iot.api.bridge.FilterContext;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.List;

/**
 * Discovers {@link BridgeEventFilter} beans and chains them in priority order.
 * First {@link FilterAction.Suppress} short-circuits; all {@link FilterAction.Forward}
 * results mean the event passes through.
 */
@ApplicationScoped
public class BridgeFilterChain {

    private final List<BridgeEventFilter> filters;

    @Inject
    public BridgeFilterChain(@Any Instance<BridgeEventFilter> discovered) {
        this.filters = discovered.stream()
                .sorted(Comparator.comparingInt(BridgeEventFilter::priority))
                .toList();
    }

    /** Test constructor — accepts a pre-built filter list. */
    BridgeFilterChain(List<BridgeEventFilter> filters) {
        this.filters = filters.stream()
                .sorted(Comparator.comparingInt(BridgeEventFilter::priority))
                .toList();
    }

    /**
     * Execute all filters in priority order against the given event.
     * Returns {@link FilterAction.Forward} if no filter suppresses,
     * or the first {@link FilterAction.Suppress} encountered.
     */
    public Uni<FilterAction> execute(StateChangeEvent event, FilterContext ctx) {
        if (filters.isEmpty()) {
            return Uni.createFrom().item(new FilterAction.Forward());
        }
        return executeFrom(0, event, ctx);
    }

    private Uni<FilterAction> executeFrom(int index, StateChangeEvent event, FilterContext ctx) {
        if (index >= filters.size()) {
            return Uni.createFrom().item(new FilterAction.Forward());
        }
        return filters.get(index).filter(event, ctx)
                .flatMap(action -> {
                    if (action instanceof FilterAction.Suppress) {
                        return Uni.createFrom().item(action);
                    }
                    return executeFrom(index + 1, event, ctx);
                });
    }
}
