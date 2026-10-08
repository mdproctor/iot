package io.casehub.iot.desiredstate;

import io.casehub.desiredstate.api.RevertMode;
import io.casehub.iot.api.TriggerSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OverrideRuleParsingTest {

    private IoTPresetResolver resolver;

    @BeforeEach
    void setUp() throws URISyntaxException {
        var dirUrl = getClass().getClassLoader().getResource("presets");
        String presetDir = Path.of(dirUrl.toURI()).toString();
        var loader = new IoTGoalLoader();
        resolver = new IoTPresetResolver(loader, presetDir);
    }

    @Test
    void resolveOverrides_parsesOverridesSection() {
        List<OverrideRule> rules = resolver.resolveOverrides("night-mode-with-overrides");

        assertThat(rules).hasSize(2);

        OverrideRule motionRule = rules.get(0);
        assertThat(motionRule.trigger()).isEqualTo(TriggerSource.MOTION);
        assertThat(motionRule.source()).isEqualTo("presence-sensor-1");
        assertThat(motionRule.targets()).isEqualTo("light-hallway");
        assertThat(motionRule.revert().mode()).isEqualTo(RevertMode.DURATION);
        assertThat(motionRule.revert().value()).isEqualTo("30m");

        OverrideRule manualRule = rules.get(1);
        assertThat(manualRule.trigger()).isEqualTo(TriggerSource.MANUAL);
        assertThat(manualRule.source()).isNull();
        assertThat(manualRule.targets()).isEqualTo("light-bedroom");
    }

    @Test
    void resolveOverrides_noOverridesSection_emptyList() {
        List<OverrideRule> rules = resolver.resolveOverrides("standalone");
        assertThat(rules).isEmpty();
    }

    @Test
    void resolveOverrides_nightMode_noOverrides_emptyList() {
        List<OverrideRule> rules = resolver.resolveOverrides("night-mode");
        assertThat(rules).isEmpty();
    }
}
