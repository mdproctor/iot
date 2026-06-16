package io.casehub.iot.api.bridge;

import java.util.Objects;

public sealed interface FilterAction {

    record Forward() implements FilterAction {}

    record Suppress(String reason) implements FilterAction {
        public Suppress {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
