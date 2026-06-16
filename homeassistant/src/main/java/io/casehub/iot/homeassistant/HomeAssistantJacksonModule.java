package io.casehub.iot.homeassistant;

import com.fasterxml.jackson.databind.module.SimpleModule;
import io.casehub.iot.api.DeviceTypeIdResolver;
import jakarta.inject.Singleton;

/**
 * Jackson module that registers Home Assistant supplement types
 * with the {@link DeviceTypeIdResolver}.
 *
 * <p>When this module is loaded (via CDI in Quarkus, or manually in tests),
 * HA supplement types will deserialize to their concrete classes instead
 * of falling back to common parent types.
 */
@Singleton
public class HomeAssistantJacksonModule extends SimpleModule {

    public HomeAssistantJacksonModule() {
        super("HomeAssistantJacksonModule");
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        DeviceTypeIdResolver.registerType("THERMOSTAT:HomeAssistantThermostat", HomeAssistantThermostat.class);
        DeviceTypeIdResolver.registerType("LIGHT:HomeAssistantLight", HomeAssistantLight.class);
        DeviceTypeIdResolver.registerType("LOCK:HomeAssistantLock", HomeAssistantLock.class);
    }
}
