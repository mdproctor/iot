package io.casehub.iot.desiredstate;

import io.casehub.desiredstate.api.RevertCondition;
import io.casehub.desiredstate.api.RevertMode;
import io.casehub.iot.api.TriggerSource;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OverrideRuleTest {

    @Test
    void revertConfigDuration_convertsToOnDuration() {
        var config = new RevertConfig(RevertMode.DURATION, "30m");
        RevertCondition condition = config.toRevertCondition();
        assertThat(condition).isInstanceOf(RevertCondition.OnDuration.class);
        assertThat(((RevertCondition.OnDuration) condition).duration()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void revertConfigDurationHours_convertsToOnDuration() {
        var config = new RevertConfig(RevertMode.DURATION, "2h");
        RevertCondition condition = config.toRevertCondition();
        assertThat(condition).isInstanceOf(RevertCondition.OnDuration.class);
        assertThat(((RevertCondition.OnDuration) condition).duration()).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void revertConfigSchedule_convertsToOnSchedule() {
        var config = new RevertConfig(RevertMode.SCHEDULE, "0 6 * * *");
        RevertCondition condition = config.toRevertCondition();
        assertThat(condition).isInstanceOf(RevertCondition.OnSchedule.class);
        assertThat(((RevertCondition.OnSchedule) condition).cronExpression()).isEqualTo("0 6 * * *");
    }

    @Test
    void revertConfigNever_convertsToNever() {
        var config = new RevertConfig(RevertMode.NEVER, null);
        RevertCondition condition = config.toRevertCondition();
        assertThat(condition).isInstanceOf(RevertCondition.Never.class);
    }

    @Test
    void revertConfigInvalidDuration_throws() {
        var config = new RevertConfig(RevertMode.DURATION, "abc");
        assertThatThrownBy(config::toRevertCondition)
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overrideRuleWithSource() {
        var rule = new OverrideRule(TriggerSource.MOTION, "hallway/presence-sensor-1",
                "hallway/LIGHT", new RevertConfig(RevertMode.DURATION, "30m"));
        assertThat(rule.trigger()).isEqualTo(TriggerSource.MOTION);
        assertThat(rule.source()).isEqualTo("hallway/presence-sensor-1");
        assertThat(rule.targets()).isEqualTo("hallway/LIGHT");
    }

    @Test
    void overrideRuleWithoutSource() {
        var rule = new OverrideRule(TriggerSource.MANUAL, null,
                "bedroom/LIGHT", new RevertConfig(RevertMode.DURATION, "60m"));
        assertThat(rule.source()).isNull();
    }
}
