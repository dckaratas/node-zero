package com.nodezero.sim.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConditionTest {

    private static ToDoubleFunction<String> metrics(Map<String, Double> values) {
        return name -> values.get(name);
    }

    @Test
    void comparesMetricWithNumber() {
        Condition c = Condition.parse("rho >= 0.9");

        assertThat(c.test(metrics(Map.of("rho", 0.95)))).isTrue();
        assertThat(c.test(metrics(Map.of("rho", 0.85)))).isFalse();
    }

    @Test
    void comparesMetricWithMetric() {
        Condition c = Condition.parse("queue >= accept_count");

        assertThat(c.test(metrics(Map.of("queue", 100.0, "accept_count", 100.0)))).isTrue();
        assertThat(c.test(metrics(Map.of("queue", 99.0, "accept_count", 100.0)))).isFalse();
    }

    @Test
    void supportsAllOperators() {
        ToDoubleFunction<String> one = metrics(Map.of("x", 1.0));

        assertThat(Condition.parse("x > 0").test(one)).isTrue();
        assertThat(Condition.parse("x < 2").test(one)).isTrue();
        assertThat(Condition.parse("x <= 1").test(one)).isTrue();
        assertThat(Condition.parse("x == 1").test(one)).isTrue();
        assertThat(Condition.parse("x != 1").test(one)).isFalse();
    }

    @Test
    void printsInSourceForm() {
        assertThat(Condition.parse("  rho>=0.9 ")).hasToString("rho >= 0.9");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "rho", "rho >= ", "rho >> 0.9", "RHO >= 1", "rho >= NaN", "rho >= 1 && x > 2",
        "System.exit(0) > 1"})
    void rejectsAnythingElse(String text) {
        assertThatThrownBy(() -> Condition.parse(text)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesConstantAndRampMultipliers() {
        assertThat(Multiplier.parse("3.0").isConstant()).isTrue();
        Multiplier ramp = Multiplier.parse("1.0 -> 3.0");

        assertThat(ramp.at(0)).isEqualTo(1.0);
        assertThat(ramp.at(0.5)).isEqualTo(2.0);
        assertThat(ramp.at(1)).isEqualTo(3.0);
        assertThatThrownBy(() -> Multiplier.parse("fast")).isInstanceOf(IllegalArgumentException.class);
    }
}
