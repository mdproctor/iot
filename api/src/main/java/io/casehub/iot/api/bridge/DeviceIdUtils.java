package io.casehub.iot.api.bridge;

import java.util.Objects;

/**
 * Utility methods for namespaced device IDs of the form {@code {tenancyId}/{localId}}.
 */
public final class DeviceIdUtils {

    private DeviceIdUtils() {}

    /**
     * Strip the tenancy prefix from a namespaced device ID.
     * Returns the original string if no {@code /} separator is present.
     */
    public static String stripPrefix(String namespacedId) {
        Objects.requireNonNull(namespacedId, "namespacedId");
        int slash = namespacedId.indexOf('/');
        return slash >= 0 ? namespacedId.substring(slash + 1) : namespacedId;
    }

    /**
     * Extract the tenancy ID from a namespaced device ID.
     * Returns the original string if no {@code /} separator is present.
     */
    public static String extractTenancyId(String namespacedId) {
        Objects.requireNonNull(namespacedId, "namespacedId");
        int slash = namespacedId.indexOf('/');
        return slash >= 0 ? namespacedId.substring(0, slash) : namespacedId;
    }
}
