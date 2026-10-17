package com.nodezero.sim.content;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Traffic multiplier of a phase: constant ({@code 3.0}) or a linear ramp ({@code "1.0 -> 3.0"}). */
public record Multiplier(double from, double to) {

    private static final String NUMBER = "(\\d+(?:\\.\\d+)?)";
    private static final Pattern SYNTAX = Pattern.compile("\\s*" + NUMBER + "(?:\\s*->\\s*" + NUMBER + ")?\\s*");

    public Multiplier {
        Require.nonNegative(from, "multiplier");
        Require.nonNegative(to, "multiplier");
    }

    /** YAML gives a number for {@code 3.0} and a string for {@code "1.0 -> 3.0"}. */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    static Multiplier fromYaml(Object value) {
        if (value instanceof Number number) {
            return new Multiplier(number.doubleValue(), number.doubleValue());
        }
        if (value instanceof String text) {
            return parse(text);
        }
        throw new IllegalArgumentException("multiplier must be a number or '1.0 -> 3.0': " + value);
    }

    public static Multiplier parse(String text) {
        Matcher m = SYNTAX.matcher(text == null ? "" : text);
        if (!m.matches()) {
            throw new IllegalArgumentException("multiplier must be '3.0' or '1.0 -> 3.0': '" + text + "'");
        }
        double from = Double.parseDouble(m.group(1));
        return new Multiplier(from, m.group(2) == null ? from : Double.parseDouble(m.group(2)));
    }

    public boolean isConstant() {
        return from == to;
    }

    /** Multiplier at {@code progress} in [0, 1] through the phase. */
    public double at(double progress) {
        return from + (to - from) * Math.clamp(progress, 0, 1);
    }
}
