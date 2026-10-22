package com.nodezero.sim.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.nodezero.sim.engine.Command.AnswerPostmortem;
import com.nodezero.sim.engine.Command.ApplyCounter;
import com.nodezero.sim.engine.Command.StartWave;
import com.nodezero.sim.state.GameState;
import com.nodezero.sim.state.WaveState;
import java.util.List;
import org.junit.jupiter.api.Test;

/** When codex entries open (slice 6b, decision AB; ADR-0017). */
class CodexUnlockTest {

    private static final Game GAME = Game.chapterOne(GameBalance.PRACTICE);

    private static GameState step(GameState s, Command... commands) {
        return GAME.step(s, List.of(commands), s.tick() + 1);
    }

    @Test
    void theCodexStartsClosed() {
        assertThat(GAME.observe(GAME.newState(1)).codex()).isEmpty();
    }

    @Test
    void buyingACounterOpensItsEntries() {
        GameState s = step(GAME.newState(1), new ApplyCounter("prometheus"));

        assertThat(GAME.observe(s).codex()).containsExactly("prometheus", "use-method");
    }

    @Test
    void aThreatsEntriesOpenWithItsPostmortemNotBefore() {
        GameState s = step(GAME.newState(1), new StartWave(2)); // log-storm: its entries name the cause
        while (s.wave().phase() == WaveState.Phase.RUNNING) {
            s = step(s);
        }
        assertThat(GAME.observe(s).codex()).as("no spoilers before the answer").isEmpty();

        s = step(s, new AnswerPostmortem("a")); // even a wrong answer earns the explanation

        assertThat(GAME.observe(s).codex()).containsExactly("fill-model", "log-levels", "logrotate", "predict-linear");
    }

    @Test
    void entriesOpenOnceInTheOrderTheyWereEarned() {
        GameState s = step(GAME.newState(1), new ApplyCounter("prometheus"));
        s = step(s, new ApplyCounter("grafana"));
        s = step(s, new ApplyCounter("raise-max-threads")); // tomcat-threads, use-method (again)

        assertThat(GAME.observe(s).codex())
                .containsExactly("prometheus", "use-method", "dashboards", "traffic-heatmap", "tomcat-threads");
    }
}
