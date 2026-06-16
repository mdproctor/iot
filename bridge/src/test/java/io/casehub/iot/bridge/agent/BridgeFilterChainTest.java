package io.casehub.iot.bridge.agent;

import io.casehub.iot.api.StateChangeEvent;
import io.casehub.iot.api.bridge.BridgeEventFilter;
import io.casehub.iot.api.bridge.ConnectionState;
import io.casehub.iot.api.bridge.FilterAction;
import io.casehub.iot.api.bridge.FilterContext;
import io.casehub.iot.testing.Fixtures;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BridgeFilterChainTest {

    private static final FilterContext CTX = new FilterContext(
            "tenant-1", ConnectionState.CONNECTED, "mock-provider");

    private static StateChangeEvent dummyEvent() {
        return new StateChangeEvent(
                null, Fixtures.hallwaySwitch(), Set.of(), Instant.now(), "mock-provider");
    }

    @Test
    void noFiltersForwardsAll() {
        var chain = new BridgeFilterChain(List.of());

        FilterAction result = chain.execute(dummyEvent(), CTX).await().indefinitely();

        assertThat(result).isInstanceOf(FilterAction.Forward.class);
    }

    @Test
    void singleForwardFilterForwards() {
        BridgeEventFilter forwardAll = new BridgeEventFilter() {
            @Override public int priority() { return 10; }
            @Override public Uni<FilterAction> filter(StateChangeEvent event, FilterContext ctx) {
                return Uni.createFrom().item(new FilterAction.Forward());
            }
        };

        var chain = new BridgeFilterChain(List.of(forwardAll));

        FilterAction result = chain.execute(dummyEvent(), CTX).await().indefinitely();

        assertThat(result).isInstanceOf(FilterAction.Forward.class);
    }

    @Test
    void suppressShortCircuits() {
        BridgeEventFilter suppress = new BridgeEventFilter() {
            @Override public int priority() { return 1; }
            @Override public Uni<FilterAction> filter(StateChangeEvent event, FilterContext ctx) {
                return Uni.createFrom().item(new FilterAction.Suppress("rate-limited"));
            }
        };

        BridgeEventFilter bomb = new BridgeEventFilter() {
            @Override public int priority() { return 2; }
            @Override public Uni<FilterAction> filter(StateChangeEvent event, FilterContext ctx) {
                throw new AssertionError("Should not be called after suppress");
            }
        };

        var chain = new BridgeFilterChain(List.of(suppress, bomb));

        FilterAction result = chain.execute(dummyEvent(), CTX).await().indefinitely();

        assertThat(result).isInstanceOf(FilterAction.Suppress.class);
        assertThat(((FilterAction.Suppress) result).reason()).isEqualTo("rate-limited");
    }

    @Test
    void filtersExecuteInPriorityOrder() {
        List<String> executionOrder = new ArrayList<>();

        BridgeEventFilter second = new BridgeEventFilter() {
            @Override public int priority() { return 20; }
            @Override public Uni<FilterAction> filter(StateChangeEvent event, FilterContext ctx) {
                executionOrder.add("priority-20");
                return Uni.createFrom().item(new FilterAction.Forward());
            }
        };

        BridgeEventFilter first = new BridgeEventFilter() {
            @Override public int priority() { return 5; }
            @Override public Uni<FilterAction> filter(StateChangeEvent event, FilterContext ctx) {
                executionOrder.add("priority-5");
                return Uni.createFrom().item(new FilterAction.Forward());
            }
        };

        // Intentionally pass in wrong order to verify sorting
        var chain = new BridgeFilterChain(List.of(second, first));

        FilterAction result = chain.execute(dummyEvent(), CTX).await().indefinitely();

        assertThat(result).isInstanceOf(FilterAction.Forward.class);
        assertThat(executionOrder).containsExactly("priority-5", "priority-20");
    }
}
