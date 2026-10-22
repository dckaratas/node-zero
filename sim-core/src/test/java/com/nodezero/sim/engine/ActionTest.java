package com.nodezero.sim.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.nodezero.sim.engine.Command.ApplyCounter;
import com.nodezero.sim.engine.Command.RunAction;
import com.nodezero.sim.engine.Command.StartWave;
import com.nodezero.sim.model.FillState;
import com.nodezero.sim.model.OccupancyState;
import com.nodezero.sim.state.ActiveThreat;
import com.nodezero.sim.state.GameState;
import com.nodezero.sim.state.ResourceId;
import com.nodezero.sim.state.WaveState;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Incident actions and the crew (slice 7b, ADR-0018 AF/AG/AH). */
class ActionTest {

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

    private static double disk(GameState s) {
        return ((FillState) s.resource(ResourceId.DISK).state()).level();
    }

    private static OccupancyState pool(GameState s, ResourceId id) {
        return (OccupancyState) s.resource(id).state();
    }

    @Test
    void actionsCostCrewAndCoolDown() {
        GameState s = step(GAME.newState(1), new RunAction("restart-app"));
        assertThat(GAME.observe(s).crew()).isEqualTo(8 - 2);
        assertThat(GAME.observe(s).actions()).singleElement()
                .satisfies(run -> assertThat(run.readyTick() - run.startTick()).isEqualTo(300));

        GameState cooling = s;
        assertThatThrownBy(() -> step(cooling, new RunAction("restart-app")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cooling down");

        s = step(s, new RunAction("rollback")); // 3 crew left
        s = step(s, new RunAction("cancel-queries"), new RunAction("clear-disk", "archives"));
        assertThat(GAME.observe(s).crew()).isEqualTo(1);
        GameState tired = idle(s, 30); // cooled down, but no new shift outside a wave
        assertThatThrownBy(() -> step(tired, new RunAction("restart-app")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("needs 2 crew, only 1 left");
    }

    @Test
    void theStationReportsTheAppDownForTheRestartWindowOnly() {
        GameState s = step(GAME.newState(1), new RunAction("restart-app")); // downtime 0-10 s
        assertThat(GAME.observe(s).station().appDown()).isTrue();
        assertThat(GAME.observe(idle(s, 9.8)).station().appDown()).isTrue();
        assertThat(GAME.observe(idle(s, 10.1)).station().appDown()).isFalse();
    }

    @Test
    void countersWithToilUseTheSameCrewAndAFinishedWaveRestsIt() {
        GameState s = step(GAME.newState(1), new ApplyCounter("vertical-scale-cpu")); // toil 1
        assertThat(GAME.observe(s).crew()).isEqualTo(7);

        s = step(s, new StartWave(1));
        while (s.wave().phase() == WaveState.Phase.RUNNING) {
            s = step(s);
        }
        assertThat(GAME.observe(s).crew()).as("new shift").isEqualTo(8);
    }

    @Test
    void aRestartDropsTheQueueGoesDarkAndRunsColdForAWhile() {
        GameState s = idle(step(GAME.newState(1), new StartWave(3)), 30); // surge overloads 4 cores
        assertThat(pool(s, ResourceId.THREADS).pending()).isPositive();
        double failedBefore = s.wave().failedRequests();

        s = step(s, new RunAction("restart-app"));
        assertThat(s.wave().failedRequests() - failedBefore)
                .as("queued and in-flight requests are lost").isGreaterThan(pool(s, ResourceId.THREADS).active());
        assertThat(pool(s, ResourceId.DB_POOL).backlog()).isZero();

        GameState dark = idle(s, 5);
        assertThat(GAME.observe(dark).burnRate()).as("downtime: every request fails").isGreaterThan(100);
        GameState back = idle(s, 40);
        assertThat(GAME.observe(back).actions().get(0).endTick()).isLessThan(back.tick());
    }

    @Test
    void truncateFreesTheDiskButRmOnlyFreesItAfterARestart() {
        GameState storm = idle(step(GAME.newState(1), new StartWave(2)), 60); // DEBUG logs fill the disk
        double full = disk(storm);
        double logs = full - StationLayout.node01().initialDiskLevel();
        assertThat(logs).isGreaterThan(5);

        GameState truncated = step(storm, new RunAction("clear-disk", "truncate"));
        assertThat(disk(truncated)).isCloseTo(full - 0.9 * logs, within(0.5));

        GameState removed = step(storm, new RunAction("clear-disk", "rm"));
        assertThat(disk(removed)).as("the application still holds the deleted file open").isCloseTo(full, within(0.5));
        GameState restarted = step(removed, new RunAction("restart-app"));
        assertThat(disk(restarted)).isCloseTo(full - 0.9 * logs, within(0.5));
    }

    @Test
    void oldArchivesCanBeDeletedOnlyOnce() {
        GameState s = step(GAME.newState(1), new RunAction("clear-disk", "archives"));
        double after = disk(s);
        assertThat(after).isCloseTo(StationLayout.node01().initialDiskLevel() - 8, within(0.1));

        s = idle(s, 10);
        double before = disk(s);
        s = step(s, new RunAction("clear-disk", "archives"));
        assertThat(disk(s)).as("nothing left to delete").isGreaterThanOrEqualTo(before);
    }

    @Test
    void cancellingQueriesFreesThePoolUntilTheJobRetries() {
        GameState s = idle(step(GAME.newState(1), new StartWave(5)), 5); // the 30 s report job holds 8 of 10 connections
        double failedBefore = s.wave().failedRequests();

        s = step(s, new RunAction("cancel-queries"));
        assertThat(s.wave().failedRequests()).isGreaterThan(failedBefore);
        double pausedActive = pool(idle(s, 5), ResourceId.DB_POOL).active();
        double retriedActive = pool(idle(s, 12), ResourceId.DB_POOL).active();
        assertThat(pausedActive).as("job paused").isLessThan(retriedActive);
    }

    @Test
    void rollbackUndoesOnlyWhatADeployCaused() {
        GameState storm = step(GAME.newState(1), new StartWave(2));
        GameState rolledBack = step(storm, new RunAction("rollback"));
        assertThat(rolledBack.progress().activeThreats()).isEmpty();

        GameState surge = step(GAME.newState(1), new StartWave(3));
        GameState notADeploy = step(surge, new RunAction("rollback"));
        assertThat(notADeploy.progress().activeThreats()).extracting(ActiveThreat::id).containsExactly("traffic-surge");
    }

    @Test
    void runningAnActionOpensItsCodexEntry() {
        GameState s = step(GAME.newState(1), new RunAction("clear-disk", "rm"));
        assertThat(GAME.observe(s).codex()).containsExactly("deleted-open-files");
    }
}
