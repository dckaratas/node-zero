package com.nodezero.sim.content;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.function.ToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Effect trigger from YAML, deliberately tiny: {@code <metric> <op> <number | metric>},
 * e.g. {@code "rho >= 0.9"} or {@code "queue >= accept_count"}. No expression engine, so content
 * cannot run code and evaluation stays deterministic.
 */
public record Condition(String metric, Operator operator, Operand threshold) {

    private static final Pattern SYNTAX =
            Pattern.compile("\\s*([a-z][a-z0-9_]*)\\s*(>=|<=|==|!=|>|<)\\s*([a-z][a-z0-9_]*|-?\\d+(\\.\\d+)?)\\s*");

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Condition parse(String text) {
        Matcher m = SYNTAX.matcher(text == null ? "" : text);
        if (!m.matches()) {
            throw new IllegalArgumentException(
                    "condition must look like '<metric> <op> <number|metric>': '" + text + "'");
        }
        String right = m.group(3);
        Operand threshold = Character.isLetter(right.charAt(0))
                ? new Operand.Metric(right)
                : new Operand.Literal(Double.parseDouble(right));
        return new Condition(m.group(1), Operator.of(m.group(2)), threshold);
    }

    public boolean test(ToDoubleFunction<String> metrics) {
        return operator.test(metrics.applyAsDouble(metric), threshold.resolve(metrics));
    }

    @Override
    public String toString() {
        return metric + " " + operator.symbol + " " + threshold;
    }

    public enum Operator {
        GE(">="),
        LE("<="),
        EQ("=="),
        NE("!="),
        GT(">"),
        LT("<");

        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        static Operator of(String symbol) {
            for (Operator op : values()) {
                if (op.symbol.equals(symbol)) {
                    return op;
                }
            }
            throw new IllegalArgumentException("unknown operator: " + symbol);
        }

        boolean test(double left, double right) {
            return switch (this) {
                case GE -> left >= right;
                case LE -> left <= right;
                case EQ -> left == right;
                case NE -> left != right;
                case GT -> left > right;
                case LT -> left < right;
            };
        }
    }

    public sealed interface Operand {

        double resolve(ToDoubleFunction<String> metrics);

        record Literal(double value) implements Operand {
            @Override
            public double resolve(ToDoubleFunction<String> metrics) {
                return value;
            }

            @Override
            public String toString() {
                return Double.toString(value);
            }
        }

        record Metric(String name) implements Operand {
            @Override
            public double resolve(ToDoubleFunction<String> metrics) {
                return metrics.applyAsDouble(name);
            }

            @Override
            public String toString() {
                return name;
            }
        }
    }
}
