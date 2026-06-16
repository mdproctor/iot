package io.casehub.iot.openhab;

import com.fasterxml.jackson.databind.module.SimpleModule;
import io.casehub.iot.api.DeviceTypeIdResolver;
import jakarta.inject.Singleton;

/**
 * Jackson module that registers OpenHAB supplement types
 * with the {@link DeviceTypeIdResolver}.
 *
 * <p>When this module is loaded (via CDI in Quarkus, or manually in tests),
 * OH supplement types will deserialize to their concrete classes instead
 * of falling back to common parent types.
 */
@Singleton
public class OpenHabJacksonModule extends SimpleModule {

    public OpenHabJacksonModule() {
        super("OpenHabJacksonModule");
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        DeviceTypeIdResolver.registerType("THERMOSTAT:OpenHabThermostat", OpenHabThermostat.class);
        DeviceTypeIdResolver.registerType("LIGHT:OpenHabLight", OpenHabLight.class);
        DeviceTypeIdResolver.registerType("COVER:OpenHabRollershutter", OpenHabRollershutter.class);
    }
}
