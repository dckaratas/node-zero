package com.nodezero.server.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.nodezero.sim.engine.Command;
import com.nodezero.sim.engine.Game;
import com.nodezero.sim.engine.Observation;
import com.nodezero.sim.state.GameState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Session logic without threads or sockets: ticks are driven by hand. */
class SessionTest {

    private static Game game;

    @BeforeAll
    static void setUp() {
        game = Game.chapterOne();
    }

    private static final class Recorder implements SessionListener {
        final List<Observation> observations = new ArrayList<>();
        final List<String> rejections = new ArrayList<>();
        final List<Boolean> pauses = new ArrayList<>();

        @Override
        public void onObservation(Observation observation) {
            observations.add(observation);
        }

        @Override
        public void onRejected(Command command, String reason) {
            rejections.add(reason);
        }

        @Override
        public void onPauseChanged(boolean paused) {
            pauses.add(paused);
        }

        @Override
        public void onFailure(Throwable error) {
            throw new AssertionError(error);
        }
    }

    private final Recorder recorder = new Recorder();
    private final Session session = new Session("s1", 42, game, recorder);

    private void ticks(int n) {
        for (int i = 0; i < n; i++) {
            session.tick();
        }
    }

    @Test
    void eachTickAdvancesTheGameAndSendsAnObservation() {
        ticks(3);

        assertThat(session.state().tick()).isEqualTo(3);
        assertThat(recorder.observations).extracting(Observation::tick).containsExactly(1L, 2L, 3L);
    }

    @Test
    void snapshotCadenceObservesOnlyEveryNthTick() {
        Session everyOther = new Session("s2", 42, game, 2, recorder);
        for (int i = 0; i < 6; i++) {
            everyOther.tick();
        }

        assertThat(everyOther.state().tick()).isEqualTo(6);
        assertThat(recorder.observations).extracting(Observation::tick).containsExactly(2L, 4L, 6L);
    }

    @Test
    void submittedCommandIsAppliedOnTheNextTickAndLogged() {
        ticks(2);
        session.submit(new Command.StartWave(1));
        ticks(1);

        assertThat(session.state().wave().number()).isEqualTo(1);
        assertThat(session.log()).containsExactly(new Session.LoggedCommand(3, new Command.StartWave(1)));
    }

    @Test
    void invalidCommandIsRejectedWhileValidOnesInTheSameTickStillApply() {
        session.submit(new Command.ApplyCounter("no-such-counter"));
        session.submit(new Command.ApplyCounter("prometheus"));
        ticks(1);

        assertThat(recorder.rejections).singleElement().asString().contains("unknown counter");
        assertThat(session.state().progress().isInstalled("prometheus")).isTrue();
        assertThat(session.log()).hasSize(1);
    }

    @Test
    void commandsThatConflictTogetherAreRejectedInArrivalOrder() {
        // 1000 credits: the 400 upgrade fits, then logrotate fits, then grafana needs prometheus first.
        session.submit(new Command.ApplyCounter("vertical-scale-cpu"));
        session.submit(new Command.ApplyCounter("expand-disk"));
        session.submit(new Command.ApplyCounter("logrotate"));
        session.submit(new Command.ApplyCounter("nginx-rate-limit"));
        ticks(1);

        // 400 + 300 + 150 = 850; the 100-credit rate limit still fits (950), so nothing is over budget yet
        assertThat(session.state().progress().credits()).isEqualTo(50);
        session.submit(new Command.ApplyCounter("statement-timeout")); // 50 credits: fits exactly
        session.submit(new Command.ApplyCounter("bulkhead-pool"));     // 150: does not
        ticks(1);

        assertThat(session.state().progress().credits()).isZero();
        assertThat(recorder.rejections).singleElement().asString().contains("only 0 left");
    }

    @Test
    void pauseStopsTimeAndHeldCommandsApplyAfterResume() {
        ticks(2);
        session.pause();
        ticks(5);
        session.submit(new Command.StartWave(1));
        ticks(5);

        assertThat(session.state().tick()).isEqualTo(2);
        assertThat(session.state().wave().number()).isZero();

        session.resume();
        ticks(1);

        assertThat(session.state().tick()).isEqualTo(3);
        assertThat(session.state().wave().number()).isEqualTo(1);
        assertThat(recorder.pauses).containsExactly(true, false);
    }

    @Test
    void seedAndLogReplayTheSessionExactly() {
        session.submit(new Command.ApplyCounter("prometheus"));
        ticks(50);
        session.submit(new Command.StartWave(2));
        ticks(300);
        session.submit(new Command.ApplyCounter("log-level-info"));
        ticks(200);

        GameState replayed = game.newState(session.seed());
        int next = 0;
        List<Session.LoggedCommand> log = session.log();
        for (long tick = 1; tick <= session.state().tick(); tick++) {
            List<Command> commands = new ArrayList<>();
            while (next < log.size() && log.get(next).tick() == tick) {
                commands.add(log.get(next++).command());
            }
            replayed = game.step(replayed, commands, tick);
        }

        assertThat(replayed).isEqualTo(session.state());
    }
}
