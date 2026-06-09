package io.casehub.iot.api;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

public record Temperature(BigDecimal value, TemperatureUnit unit) {

    public Temperature {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(unit, "unit");
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Temperature other)) return false;
        return unit == other.unit && value.compareTo(other.value) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(unit, value.stripTrailingZeros());
    }

    public enum TemperatureUnit { CELSIUS, FAHRENHEIT }

    public Temperature toCelsius() {
        if (unit == TemperatureUnit.CELSIUS) return this;
        BigDecimal celsius = value.subtract(BigDecimal.valueOf(32))
            .multiply(BigDecimal.valueOf(5))
            .divide(BigDecimal.valueOf(9), MathContext.DECIMAL64);
        return new Temperature(celsius, TemperatureUnit.CELSIUS);
    }

    public Temperature toFahrenheit() {
        if (unit == TemperatureUnit.FAHRENHEIT) return this;
        BigDecimal fahrenheit = value.multiply(BigDecimal.valueOf(9))
            .divide(BigDecimal.valueOf(5), MathContext.DECIMAL64)
            .add(BigDecimal.valueOf(32));
        return new Temperature(fahrenheit, TemperatureUnit.FAHRENHEIT);
    }
}
