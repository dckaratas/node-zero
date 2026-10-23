package com.nodezero.sim.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.nodezero.sim.engine.Command.ApplyCounter;
import com.nodezero.sim.engine.Command.StartWave;
import com.nodezero.sim.engine.Command.UnlockInsight;
import com.nodezero.sim.model.FillState;
import com.nodezero.sim.model.OccupancyModel;
import com.nodezero.sim.model.UtilizationModel;
import com.nodezero.sim.model.UtilizationState;
import com.nodezero.sim.state.GameState;
import com.nodezero.sim.state.ResourceId;
import com.nodezero.sim.state.WaveState.Phase;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Error budget, wave lifecycle, credits and counters (slice 3b, decisions B/C/E/F/G). */
class WaveAndCounterTest {

    private static Game game;
    private static Game practice;

    @BeforeAll
    static void setUp() {
        game = Game.chapterOne();
        practice = Game.chapterOne(GameBalance.PRACTICE);
    }

    /** Small script runner: commands keyed by tick, run until a tick or until the wave is over. */
    private static final class Session {
        final Game rules;
        GameState state;
        final Map<Long, List<Command>> script = new LinkedHashMap<>();
        double peakPoolRejectedRps;

        Session() {
            this(game);
        }

        Session(Game rules) {
            this.rules = rules;
            this.state = rules.newState(7);
        }

        Session at(long tick, Command... commands) {
            script.computeIfAbsent(tick, t -> new ArrayList<>()).addAll(List.of(commands));
            return this;
        }

        GameState runUntil(long lastTick) {
            for (long tick = state.tick() + 1; tick <= lastTick; tick++) {
                step(tick);
            }
            return state;
        }

        GameState runWave() {
            long tick = state.tick();
            do {
                step(++tick);
            } while (state.wave().phase() == Phase.RUNNING || state.wave().number() == 0);
            return state;
        }

        private void step(long tick) {
            state = rules.step(state, script.getOrDefault(tick, List.of()), tick);
            double rejected = ((com.nodezero.sim.model.OccupancyState) state.resource(ResourceId.DB_POOL).state())
                    .rejectedRps();
            peakPoolRejectedRps = Math.max(peakPoolRejectedRps, rejected);
        }
    }

    @Test
    void quietWaveCompletesWithFullShieldAndPaysTheReward() {
        GameState s = new Session().at(1, new StartWave(1)).runWave();

        assertThat(s.wave().phase()).isEqualTo(Phase.COMPLETE);
        assertThat(s.wave().shield(0.99)).isCloseTo(1.0, within(1e-9));
        assertThat(s.tick()).isEqualTo(600); // 60 s quiet wave
        assertThat(s.progress().credits()).isEqualTo(1000 + 300);
    }

    @Test
    void festivalWithoutPreparationBurnsTheShield() {
        GameState s = new Session().at(1, new StartWave(3)).runWave();

        assertThat(s.wave().phase()).isEqualTo(Phase.FAILED);
        assertThat(s.wave().shield(0.99)).isZero();
        assertThat(s.progress().credits()).isEqualTo(1000); // no reward for a failed wave
    }

    @Test
    void reactorUpgradeBeforeTheFestivalSavesTheWave() {
        // Bought in preparation: the 20 s build and its cut-over outage end before the wave, outside the budget.
        GameState s = new Session()
                .at(1, new ApplyCounter("vertical-scale-cpu", "maintenance"))
                .at(300, new StartWave(3))
                .runWave();

        assertThat(((UtilizationModel) s.resource(ResourceId.CPU).model()).servers()).isEqualTo(8);
        assertThat(s.wave().phase()).isEqualTo(Phase.COMPLETE);
        assertThat(s.wave().shield(0.99)).isGreaterThan(0.9);
        // 1000 - 400 cost + reward scaled by shield - 40 upkeep
        assertThat(s.progress().credits()).isBetween(1000 - 400 + 270 - 40, 1000 - 400 + 300 - 40);
    }

    @Test
    void upgradingDuringTheWaveCostsAnOutageAtTheCutOver() {
        GameState before = new Session().at(1, new StartWave(3)).at(2, new ApplyCounter("vertical-scale-cpu", "maintenance"))
                .runUntil(152);
        GameState after = new Session().at(1, new StartWave(3)).at(2, new ApplyCounter("vertical-scale-cpu", "maintenance"))
                .runUntil(202);

        // The new VM is prepared for 15 s, then the reactor is offline for the 5 s cut-over: every request fails.
        double requests = after.wave().requests() - before.wave().requests();
        assertThat(after.wave().failedRequests() - before.wave().failedRequests()).isCloseTo(requests, within(requests * 0.1));
    }

    @Test
    void aLiveMigrationCostsMoreButNeverGoesDark() {
        // During a calm wave: 30 s of build, no cut-over outage.
        GameState s = new Session().at(1, new StartWave(1)).at(2, new ApplyCounter("vertical-scale-cpu", "live"))
                .runUntil(330);

        assertThat(((UtilizationModel) s.resource(ResourceId.CPU).model()).servers()).isEqualTo(8);
        assertThat(s.progress().credits()).isEqualTo(1000 - 650);
        assertThat(s.wave().failedRequests()).isLessThan(1);
    }

    @Test
    void rateLimitProtectsTheReactorButUsersStillFail() {
        // Plateau: 300 rps arriving, 150 admitted. Practice mode so the wave reaches the plateau.
        GameState plateau = new Session(practice)
                .at(1, new ApplyCounter("nginx-rate-limit"))
                .at(50, new StartWave(3))
                .runUntil(600);
        UtilizationState cpu = (UtilizationState) plateau.resource(ResourceId.CPU).state();
        assertThat(cpu.rho()).isCloseTo(0.75, within(0.01));
        assertThat(cpu.latencyMs()).isLessThan(100);

        // 429s still count against the budget: the reactor is fine, the users are not.
        GameState real = new Session().at(1, new ApplyCounter("nginx-rate-limit")).at(50, new StartWave(3)).runWave();
        assertThat(real.wave().phase()).isEqualTo(Phase.FAILED);
    }

    @Test
    void logStormPersistsIntoLaterWaves() {
        Session session = new Session().at(1, new StartWave(2));
        GameState afterStorm = session.runWave();
        assertThat(afterStorm.progress().isThreatActive("log-storm")).isTrue();

        // The postmortem must be answered before the next wave (slice 6a); same tick, answer first.
        session.at(afterStorm.tick() + 1, new Command.AnswerPostmortem("b"), new StartWave(3));
        GameState later = session.runUntil(afterStorm.tick() + 300);

        // DEBUG logs filled the cargo hold in wave 2 and keep it full during the next wave.
        FillState disk = (FillState) later.resource(ResourceId.DISK).state();
        assertThat(disk.level()).isCloseTo(100, within(0.01));
        assertThat(later.progress().isThreatActive("traffic-surge")).isTrue();
    }

    @Test
    void fixingTheLogLevelStopsTheStorm() {
        GameState fixed = new Session()
                .at(1, new StartWave(2))
                .at(2, new ApplyCounter("log-level-info"))
                .runWave();
        GameState unfixed = new Session().at(1, new StartWave(2)).runWave();

        double fixedLevel = ((FillState) fixed.resource(ResourceId.DISK).state()).level();
        double unfixedLevel = ((FillState) unfixed.resource(ResourceId.DISK).state()).level();
        assertThat(fixedLevel).isLessThan(unfixedLevel - 15);
    }

    @Test
    void biggerDiskOnlyPostponesTheProblem() {
        GameState s = new Session().at(1, new ApplyCounter("expand-disk")).at(2, new StartWave(2)).runWave();

        FillState disk = (FillState) s.resource(ResourceId.DISK).state();
        assertThat(disk.fillRatio()).isLessThan(0.6);
        assertThat(disk.secondsToFull()).isFinite(); // still filling
    }

    @Test
    void createIndexNeedsTheInsightFirst() {
        Session session = new Session().at(1, new StartWave(4));
        session.runUntil(1);
        GameState s = session.state;

        assertThatThrownBy(() -> game.step(s, List.of(new ApplyCounter("create-index", "concurrently")), 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pg-stat-statements");
    }

    @Test
    void indexFixesTheSlowQuery() {
        GameState s = new Session()
                .at(1, new UnlockInsight("pg-stat-statements"), new ApplyCounter("create-index", "concurrently"))
                .at(500, new StartWave(4))
                .runWave();

        assertThat(s.wave().phase()).isEqualTo(Phase.COMPLETE);
        assertThat(s.wave().shield(0.99)).isGreaterThan(0.9);
    }

    @Test
    void moreThreadsAloneDoNotSaveTheSlowQueryWave() {
        GameState s = new Session().at(1, new ApplyCounter("raise-max-threads")).at(2, new StartWave(4)).runWave();

        assertThat(((OccupancyModel) s.resource(ResourceId.THREADS).model()).poolSize()).isEqualTo(400);
        assertThat(s.wave().shield(0.99)).isLessThan(0.7);
    }

    @Test
    void nightlyJobAtPeakHourBreaksTheWave() {
        GameState s = new Session().at(1, new StartWave(5)).runWave();

        assertThat(s.wave().shield(0.99)).isLessThan(0.7);
    }

    @Test
    void bulkheadKeepsTheNightlyJobFromStarvingUsers() {
        // Installed during preparation; its 5 s setup must not overlap the job.
        GameState s = new Session().at(1, new ApplyCounter("bulkhead-pool")).at(100, new StartWave(5)).runWave();

        assertThat(s.wave().phase()).isEqualTo(Phase.COMPLETE);
    }

    @Test
    void reschedulingTheJobAwayFromPeakHourWorksBest() {
        GameState s = new Session()
                .at(1, new UnlockInsight("traffic-heatmap"), new ApplyCounter("reschedule-job"))
                .at(100, new StartWave(5))
                .runWave();

        assertThat(s.wave().phase()).isEqualTo(Phase.COMPLETE);
        assertThat(s.wave().shield(0.99)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void naiveRetryOverflowsTheConnectionPool() {
        Session plain = new Session(practice).at(1, new StartWave(5));
        plain.runWave();
        Session retry = new Session(practice).at(1, new ApplyCounter("naive-retry")).at(100, new StartWave(5));
        retry.runWave();

        // Retries double the load on an already saturated pool: Hikari starts timing requests out.
        assertThat(plain.peakPoolRejectedRps).isZero();
        assertThat(retry.peakPoolRejectedRps).isGreaterThan(10);
    }

    @Test
    void countersCostCreditsAndCannotBeBoughtTwiceOrOnCredit() {
        Session session = new Session().at(1, new ApplyCounter("vertical-scale-cpu", "maintenance"));
        GameState s = session.runUntil(1);
        assertThat(s.progress().credits()).isEqualTo(600);

        assertThatThrownBy(() -> game.step(s, List.of(new ApplyCounter("vertical-scale-cpu", "maintenance")), 2))
                .hasMessageContaining("already installed");
        GameState poorer = game.step(s, List.of(new ApplyCounter("expand-disk")), 2);
        assertThatThrownBy(() -> game.step(poorer, List.of(new ApplyCounter("create-index", "blocking")), 3))
                .hasMessageContaining("needs insight");
        GameState broke = game.step(poorer, List.of(new ApplyCounter("bulkhead-pool")), 3);
        assertThatThrownBy(() -> game.step(broke, List.of(new ApplyCounter("batch-chunking")), 4))
                .hasMessageContaining("only 150 left");
    }

    @Test
    void optionIsRequiredWhenACounterHasOptions() {
        GameState s = new Session().at(1, new UnlockInsight("pg-stat-statements")).runUntil(1);

        assertThatThrownBy(() -> game.step(s, List.of(new ApplyCounter("create-index")), 2))
                .hasMessageContaining("choose one of [blocking, concurrently]");
    }

    @Test
    void wholeGameReplaysIdentically() {
        assertThat(script()).isEqualTo(script());
    }

    private static GameState script() {
        return new Session()
                .at(1, new StartWave(2))
                .at(50, new ApplyCounter("log-level-info"))
                .at(1300, new ApplyCounter("vertical-scale-cpu", "maintenance"), new Command.AnswerPostmortem("b"), new StartWave(3))
                .runUntil(2500);
    }
}
