package io.casehub.iot.openhab.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenHabThingDto(
    @JsonProperty("UID") String uid,
    String label,
    String thingTypeUID,
    OpenHabStatusInfoDto statusInfo,
    List<OpenHabChannelDto> channels,
    String location
) {
    public List<OpenHabChannelDto> stateChannels() {
        return channels != null
            ? channels.stream().filter(OpenHabChannelDto::isStateChannel).toList()
            : List.of();
    }

    public boolean isOnline() {
        return statusInfo != null && "ONLINE".equals(statusInfo.status());
    }
}
