package io.casehub.iot.openhab.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenHabItemDto(
    String type,
    String name,
    String label,
    String state,
    List<String> tags,
    List<OpenHabItemDto> members,
    @JsonProperty("stateDescription") OpenHabStateDescriptionDto stateDescription
) {
    public Set<String> tagSet() {
        return tags != null ? new HashSet<>(tags) : Set.of();
    }
}
