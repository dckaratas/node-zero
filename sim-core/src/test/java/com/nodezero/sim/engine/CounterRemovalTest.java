package com.nodezero.sim.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nodezero.sim.engine.Command.ApplyCounter;
import com.nodezero.sim.engine.Command.ConfigureCounter;
import com.nodezero.sim.engine.Command.RemoveCounter;
import com.nodezero.sim.engine.Command.StartWave;
import com.nodezero.sim.model.UtilizationModel;
import com.nodezero.sim.state.GameState;
import com.nodezero.sim.state.ResourceId;
import com.nodezero.sim.state.WaveState;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Removing counters and changing their setting (slice 7c, ADR-0018 AI). */
class CounterRemovalTest {

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

    private static int cores(GameState s) {
        return ((UtilizationModel) s.resource(ResourceId.CPU).model()).servers();
    }

    private static GameState installed(String... counters) {
        GameState s = GAME.newState(1);
        for (String c : counters) {
            s = step(s, new ApplyCounter(c));
        }
        return idle(s, 10);
    }

    @Test
    void scalingDownTakesAsLongAsScalingUpAndGoesDarkAgain() {
        GameState s = installed("vertical-scale-cpu");
        assertThat(cores(s)).isEqualTo(8);
        int credits = s.progress().credits();
        int crew = GAME.observe(s).crew();

        s = step(s, new RemoveCounter("vertical-scale-cpu"));
        assertThat(GAME.observe(s).crew()).isEqualTo(crew - 1);
        GameState removing = idle(step(s, new StartWave(1)), 2);
        assertThat(cores(removing)).as("still running while it is taken out").isEqualTo(8);
        assertThat(GAME.observe(removing).burnRate()).as("downtime, like the upgrade").isGreaterThan(50);

        GameState done = idle(removing, 5);
        assertThat(cores(done)).isEqualTo(4);
        assertThat(done.progress().isInstalled("vertical-scale-cpu")).isFalse();
        assertThat(done.progress().credits()).as("no refund").isEqualTo(credits);
    }

    @Test
    void aDiskCannotShrink() {
        GameState s = installed("expand-disk");
        assertThatThrownBy(() -> step(s, new RemoveCounter("expand-disk")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cannot be undone");
    }

    @Test
    void whatOthersBuildOnStaysAndRemovingMonitoringBringsTheFogBackButNotTheIgnorance() {
        GameState s = installed("prometheus", "grafana");
        GameState both = s;
        assertThatThrownBy(() -> step(both, new RemoveCounter("prometheus")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("grafana needs prometheus");

        s = idle(step(s, new RemoveCounter("grafana")), 6);
        s = idle(step(s, new RemoveCounter("prometheus")), 6);
        Observation o = GAME.observe(s);
        assertThat(o.metrics()).isEmpty();
        assertThat(o.insights()).as("what was learned stays").contains("traffic-heatmap");
    }

    @Test
    void onlyARunningCounterCanBeRemovedOnce() {
        GameState building = step(GAME.newState(1), new ApplyCounter("prometheus"));
        assertThatThrownBy(() -> step(building, new RemoveCounter("prometheus")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("still being built");

        GameState removing = step(installed("prometheus"), new RemoveCounter("prometheus"));
        assertThatThrownBy(() -> step(removing, new RemoveCounter("prometheus")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already being removed");
        assertThatThrownBy(() -> step(GAME.newState(1), new RemoveCounter("prometheus")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not installed");
    }

    @Test
    void aRemovedCounterCostsNoMoreUpkeepAndCanBeBoughtAgain() {
        GameState kept = installed("logrotate");
        GameState removed = idle(step(kept, new RemoveCounter("logrotate")), 6);

        int keptCredits = playQuietWave(kept).progress().credits();
        int removedCredits = playQuietWave(removed).progress().credits();
        assertThat(removedCredits - keptCredits).isEqualTo(10); // logrotate upkeep per wave

        GameState again = step(removed, new ApplyCounter("logrotate"));
        assertThat(again.progress().isInstalled("logrotate")).isTrue();
    }

    @Test
    void aHigherRateLimitLetsMoreOfTheSurgeThrough() {
        GameState s = installed("vertical-scale-cpu", "nginx-rate-limit");
        int crew = GAME.observe(s).crew();
        GameState tuned = step(s, new ConfigureCounter("nginx-rate-limit", 350));
        assertThat(GAME.observe(tuned).crew()).isEqualTo(crew - 1);
        assertThat(GAME.observe(tuned).counters()).filteredOn(c -> c.id().equals("nginx-rate-limit"))
                .singleElement().satisfies(c -> assertThat(c.setting()).isEqualTo(350.0));

        double defaultShield = playWave(s, 3).progress().results().get(0).shield();
        double tunedShield = playWave(tuned, 3).progress().results().get(0).shield();
        assertThat(defaultShield).as("150 rps: 429s burn the budget").isZero();
        assertThat(tunedShield).as("350 rps on 8 cores: the surge fits").isEqualTo(1.0);
    }

    @Test
    void settingsHaveLimitsAndOnlySomeCountersHaveOne() {
        GameState s = installed("nginx-rate-limit", "prometheus");
        assertThatThrownBy(() -> step(s, new ConfigureCounter("nginx-rate-limit", 1000)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("rate_rps must be within");
        assertThatThrownBy(() -> step(s, new ConfigureCounter("prometheus", 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("has no setting");
    }

    private static GameState playQuietWave(GameState s) {
        return playWave(s, 1);
    }

    private static GameState playWave(GameState s, int wave) {
        s = step(s, new StartWave(wave));
        while (s.wave().phase() == WaveState.Phase.RUNNING) {
            s = step(s);
        }
        return s;
    }
}
