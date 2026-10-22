package com.nodezero.sim.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nodezero.sim.engine.Command.ApplyCounter;
import com.nodezero.sim.engine.Command.RemoveAlertRule;
import com.nodezero.sim.engine.Command.RemoveCounter;
import com.nodezero.sim.engine.Command.SetAlertRule;
import com.nodezero.sim.engine.Command.StartWave;
import com.nodezero.sim.state.AlertRule;
import com.nodezero.sim.state.AlertRule.Comparison;
import com.nodezero.sim.state.GameState;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Alert rules (slice 7d, ADR-0018 AJ). */
class AlertTest {

    private static final Game GAME = Game.chapterOne(GameBalance.PRACTICE);

    private static GameState step(GameState s, Command... commands) {
        return GAME.step(s, List.of(commands), s.tick() + 1);
    }

    private static GameState idle(GameState s, double seconds) {
        for (int i = 0; i < Math.round(seconds / SimConstants.DT); i++) {
            s = step(s);
        }
        return s;
    }

    private static GameState monitored(String... counters) {
        GameState s = GAME.newState(1);
        for (String c : counters) {
            s = step(s, new ApplyCounter(c));
        }
        return idle(s, 10);
    }

    private static AlertRule rule(GameState s) {
        return GAME.observe(s).alerts().get(0);
    }

    @Test
    void anAlertCanOnlyWatchWhatMonitoringMeasures() {
        GameState blind = GAME.newState(1);
        assertThatThrownBy(() -> step(blind, new SetAlertRule("cpu_utilization", Comparison.ABOVE, 0.9, 5)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no monitoring measures it");

        GameState raw = monitored("prometheus");
        assertThatThrownBy(() -> step(raw, new SetAlertRule("latency_p99", Comparison.ABOVE, 500, 5)))
                .as("a Grafana panel").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> step(raw, new SetAlertRule("cpu_temperature", Comparison.ABOVE, 1, 5)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unknown metric");
    }

    @Test
    void rulesAreFewAndWaitAtMostTenMinutes() {
        GameState s = monitored("prometheus");
        for (int i = 0; i < Alerts.MAX_RULES; i++) {
            s = step(s, new SetAlertRule("cpu_utilization", Comparison.ABOVE, 0.5 + i / 10.0, 5));
        }
        assertThat(GAME.observe(s).alerts()).extracting(AlertRule::id).containsExactly("a1", "a2", "a3", "a4", "a5");
        GameState full = s;
        assertThatThrownBy(() -> step(full, new SetAlertRule("cpu_utilization", Comparison.ABOVE, 0.9, 5)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at most 5");

        GameState fewer = step(s, new RemoveAlertRule("a2"));
        fewer = step(fewer, new SetAlertRule("disk_usage_ratio", Comparison.ABOVE, 0.9, 0));
        assertThat(GAME.observe(fewer).alerts()).extracting(AlertRule::id).containsExactly("a1", "a3", "a4", "a5", "a6");
        GameState some = fewer;
        assertThatThrownBy(() -> step(some, new RemoveAlertRule("a2")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no alert rule a2");
        assertThatThrownBy(() -> step(monitored("prometheus"),
                new SetAlertRule("cpu_utilization", Comparison.ABOVE, 0.9, 601)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("for must be within");
    }

    @Test
    void aBreachIsPendingUntilItHasLastedThenFiresAndResolvesWhenItStops() {
        GameState s = step(monitored("prometheus"), new SetAlertRule("cpu_utilization", Comparison.ABOVE, 0.9, 5));
        assertThat(rule(s).pendingSinceTick()).as("idle station").isEqualTo(-1);

        s = step(s, new StartWave(3)); // the surge saturates the reactor within ~10 s
        while (rule(s).pendingSinceTick() < 0) {
            s = step(s);
        }
        long pendingAt = rule(s).pendingSinceTick();
        assertThat(rule(s).firing()).isFalse();

        GameState later = idle(s, 5);
        assertThat(rule(later).firing()).isTrue();
        assertThat(rule(later).firingSinceTick() - pendingAt).isEqualTo(50);

        GameState over = idle(later, 120); // the wave ends and traffic falls back
        assertThat(rule(over).firing()).isFalse();
        assertThat(rule(over).pendingSinceTick()).isEqualTo(-1);
    }

    @Test
    void aBelowRuleWithoutWaitFiresAtOnce() {
        GameState s = step(monitored("prometheus"), new SetAlertRule("request_rate", Comparison.BELOW, 500, 0));
        s = step(s);
        assertThat(rule(s).firing()).isTrue();
    }

    @Test
    void withoutDataARuleNeverFires() {
        GameState s = step(monitored("prometheus"), new SetAlertRule("request_rate", Comparison.BELOW, 500, 0));
        s = step(s);
        assertThat(rule(s).firing()).isTrue();

        s = idle(step(s, new RemoveCounter("prometheus")), 6);
        assertThat(GAME.observe(s).metrics()).isEmpty();
        assertThat(rule(s).firing()).as("no data: silent, which is its own danger").isFalse();
    }
}
