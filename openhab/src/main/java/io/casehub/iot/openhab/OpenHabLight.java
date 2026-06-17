package io.casehub.iot.openhab;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.casehub.iot.api.LightDevice;

import java.util.Map;
import java.util.Optional;

/**
 * OpenHAB supplement for {@link LightDevice}.
 * Adds OpenHAB-specific field: HSB (Hue-Saturation-Brightness) color type.
 */
@JsonDeserialize(builder = OpenHabLight.Builder.class)
public class OpenHabLight extends LightDevice {

    public static final String CAP_HSB = "hsb";

    private final OpenHabHsbType hsb;

    protected OpenHabLight(AbstractBuilder<?, ?> builder) {
        super(builder);
        this.hsb = builder.hsb;
    }

    public Optional<OpenHabHsbType> hsb() {
        return Optional.ofNullable(hsb);
    }

    @Override
    public Map<String, Object> capabilities() {
        Map<String, Object> caps = super.capabilities();
        caps.put(CAP_HSB, hsb);
        return caps;
    }

    public Builder toBuilder() {
        return new Builder()
                .deviceId(deviceId()).deviceClass(deviceClass()).label(label())
                .available(available()).lastUpdated(lastUpdated()).tenancyId(tenancyId()).providerId("openhab")
                .on(isOn()).brightness(brightness().orElse(null)).colorTemp(colorTemp().orElse(null))
                .hsb(hsb);
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder extends AbstractBuilder<OpenHabLight, Builder> {
        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public OpenHabLight build() {
            return new OpenHabLight(this);
        }
    }

    public abstract static class AbstractBuilder<T extends OpenHabLight, B extends AbstractBuilder<T, B>>
            extends LightDevice.AbstractBuilder<T, B> {
        OpenHabHsbType hsb;

        public B hsb(OpenHabHsbType hsb) {
            this.hsb = hsb;
            return self();
        }
    }
}
