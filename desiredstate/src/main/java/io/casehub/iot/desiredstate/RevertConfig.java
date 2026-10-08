package io.casehub.iot.desiredstate;

import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.desiredstate.api.RevertCondition;
import io.casehub.desiredstate.api.RevertMode;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public record RevertConfig(RevertMode mode, String value) {

    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)([mhsd])");

    public RevertCondition toRevertCondition() {
        return switch (mode) {
            case DURATION -> new RevertCondition.OnDuration(parseDuration(value));
            case SCHEDULE -> new RevertCondition.OnSchedule(value);
            case STATUS_CHANGE -> new RevertCondition.OnStatusChange(
                    Arrays.stream(value.split(","))
                            .map(String::trim)
                            .map(NodeStatus::valueOf)
                            .collect(Collectors.toSet()));
            case NEVER -> new RevertCondition.Never();
        };
    }

    private static Duration parseDuration(String input) {
        Objects.requireNonNull(input, "duration value required");
        Matcher m = DURATION_PATTERN.matcher(input.trim());
        if (!m.matches()) throw new IllegalArgumentException("Invalid duration: " + input);
        long amount = Long.parseLong(m.group(1));
        return switch (m.group(2)) {
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            case "s" -> Duration.ofSeconds(amount);
            case "d" -> Duration.ofDays(amount);
            default -> throw new IllegalArgumentException("Unknown unit: " + m.group(2));
        };
    }
}
