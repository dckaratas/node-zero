package com.nodezero.sim.engine;

import com.nodezero.sim.state.AlertRule;
import com.nodezero.sim.state.GameState;
import com.nodezero.sim.state.OpsState;
import com.nodezero.sim.state.Progress;
import java.util.ArrayList;
import java.util.List;

/**
 * Alert rules (slice 7d, ADR-0018 AJ): created and deleted by the player, evaluated on the server every
 * tick after the models advance, so they are deterministic and replay with the session. A firing alert
 * never pauses the game; it only makes sure the player hears about it.
 */
final class Alerts {

    /** Rules per station; a wall of alerts is noise (alert fatigue). */
    static final int MAX_RULES = 5;
    static final double MAX_FOR_S = 600;

    private final Rules rules;

    Alerts(Rules rules) {
        this.rules = rules;
    }

    GameState set(GameState s, Command.SetAlertRule command, long tick) {
        Progress p = s.progress();
        OpsState ops = p.ops();
        Metric metric = Metric.byName(command.metric());
        if (metric == null) {
            throw new IllegalArgumentException("unknown metric: " + command.metric());
        }
        if (!Monitoring.at(rules, p, tick).allow(metric.tier())) {
            throw new IllegalArgumentException("cannot alert on " + command.metric() + ": no monitoring measures it");
        }
        if (ops.alerts().size() >= MAX_RULES) {
            throw new IllegalArgumentException("at most " + MAX_RULES + " alert rules");
        }
        if (!Double.isFinite(command.threshold())) {
            throw new IllegalArgumentException("threshold must be a number");
        }
        if (command.forS() < 0 || command.forS() > MAX_FOR_S) {
            throw new IllegalArgumentException("for must be within [0, " + MAX_FOR_S + "] s: " + command.forS());
        }
        AlertRule rule = new AlertRule(ops.nextAlertId(), metric.metricName(), command.op(), command.threshold(),
                command.forS(), -1, -1);
        return s.withProgress(p.withOps(ops.withNewAlert(rule)));
    }

    GameState remove(GameState s, Command.RemoveAlertRule command) {
        OpsState ops = s.progress().ops();
        List<AlertRule> kept = new ArrayList<>(ops.alerts().size());
        for (AlertRule r : ops.alerts()) {
            if (!r.id().equals(command.ruleId())) {
                kept.add(r);
            }
        }
        if (kept.size() == ops.alerts().size()) {
            throw new IllegalArgumentException("no alert rule " + command.ruleId());
        }
        return s.withProgress(s.progress().withOps(ops.withAlerts(kept)));
    }

    /** Moves each rule between inactive, pending and firing; allocates only when one changes. */
    GameState evaluate(GameState s, long tick) {
        List<AlertRule> alerts = s.progress().ops().alerts();
        if (alerts.isEmpty()) {
            return s;
        }
        Monitoring monitoring = Monitoring.at(rules, s.progress(), tick);
        List<AlertRule> next = null;
        for (int i = 0; i < alerts.size(); i++) {
            AlertRule r = alerts.get(i);
            Metric metric = Metric.byName(r.metric());
            boolean breach = false;
            if (monitoring.allow(metric.tier())) {
                double value = metric.read(s);
                breach = r.op() == AlertRule.Comparison.ABOVE ? value > r.threshold() : value < r.threshold();
            }
            AlertRule updated = r;
            if (!breach) {
                if (r.pendingSinceTick() >= 0) {
                    updated = r.at(-1, -1);
                }
            } else if (r.pendingSinceTick() < 0) {
                updated = r.forS() == 0 ? r.at(tick, tick) : r.at(tick, -1);
            } else if (!r.firing() && (tick - r.pendingSinceTick()) * SimConstants.DT >= r.forS() - 1e-9) {
                updated = r.at(r.pendingSinceTick(), tick);
            }
            if (updated != r) {
                if (next == null) {
                    next = new ArrayList<>(alerts);
                }
                next.set(i, updated);
            }
        }
        return next == null ? s : s.withProgress(s.progress().withOps(s.progress().ops().withAlerts(next)));
    }
}
