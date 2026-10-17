package com.nodezero.sim.content;

import java.util.List;
import java.util.regex.Pattern;

/** Field checks used by content record constructors; messages name the YAML field. */
final class Require {

    private static final Pattern ID = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final Pattern SNAKE = Pattern.compile("[a-z][a-z0-9_]*");
    private static final Pattern TEXT_KEY = Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+");

    private Require() {}

    /** Kebab-case id, e.g. {@code traffic-surge}. */
    static String id(String value, String field) {
        return matches(value, ID, field, "kebab-case id");
    }

    /** Snake-case name, e.g. {@code cpu_utilization}. */
    static String snake(String value, String field) {
        return matches(value, SNAKE, field, "snake_case name");
    }

    /** Dotted i18n key, e.g. {@code pm.traffic_surge.q}. */
    static String textKey(String value, String field) {
        return matches(value, TEXT_KEY, field, "dotted text key");
    }

    static String nonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    static <T> T present(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    static <T> List<T> nonEmpty(List<T> values, String field) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        return List.copyOf(values);
    }

    static <T> List<T> orEmpty(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    static int atLeast(int value, int min, String field) {
        if (value < min) {
            throw new IllegalArgumentException(field + " must be >= " + min + ": " + value);
        }
        return value;
    }

    static double positive(double value, String field) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(field + " must be > 0: " + value);
        }
        return value;
    }

    static double nonNegative(double value, String field) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(field + " must be >= 0: " + value);
        }
        return value;
    }

    private static String matches(String value, Pattern pattern, String field, String kind) {
        nonBlank(value, field);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a " + kind + ": '" + value + "'");
        }
        return value;
    }
}
