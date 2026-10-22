package com.nodezero.sim.state;

/**
 * A player's alert rule (slice 7d, ADR-0018 AJ), shaped like a Prometheus alerting rule:
 * {@code metric > threshold for forS}. It is pending while the condition holds and firing once it has held
 * for {@code forS}; it resolves as soon as the condition stops. A metric the player cannot measure has no
 * data, and a rule without data never fires (Prometheus would need {@code absent()} for that).
 *
 * @param pendingSinceTick first tick of the current breach, or -1
 * @param firingSinceTick tick it started firing, or -1
 */
public record AlertRule(String id, String metric, Comparison op, double threshold, double forS,
        long pendingSinceTick, long firingSinceTick) {

    public enum Comparison {
        ABOVE,
        BELOW
    }

    public boolean firing() {
        return firingSinceTick >= 0;
    }

    public AlertRule at(long pendingSince, long firingSince) {
        return new AlertRule(id, metric, op, threshold, forS, pendingSince, firingSince);
    }
}
