package com.nodezero.sim.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.nodezero.sim.engine.Command.AnswerPostmortem;
import com.nodezero.sim.engine.Command.ApplyCounter;
import com.nodezero.sim.engine.Command.RunAction;
import com.nodezero.sim.engine.Command.StartWave;
import com.nodezero.sim.model.FillState;
import com.nodezero.sim.state.GameState;
import com.nodezero.sim.state.ResourceId;
import com.nodezero.sim.state.WaveState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Balance bands of chapter 1 (ADR-0018 AY). Each threat wave is played on a station where every earlier threat
 * was fixed in preparation, so one wave is measured at a time:
 * <ul>
 *   <li>unprepared: the wave hurts but does not wipe the shield (0.2..0.6), except the festival (see below);</li>
 *   <li>reactive: the right incident action plus the fix bought at the first symptom keeps at least 0.7;</li>
 *   <li>prepared: the fix installed before the wave keeps the shield full.</li>
 * </ul>
 * A content change that breaks a band is a balance change and needs a decision, not a new number here.
 */
class BalanceTest {

    private static final Game GAME = Game.chapterOne(GameBalance.PRACTICE);
    /** Right root cause of each threat wave of chapter 1. */
    private static final String[] RIGHT = {null, null, "b", "b", "c", "c"};

    /** The lasting fix of each threat wave, as installed in preparation. */
    private static Command fix(int wave) {
        return switch (wave) {
            case 2 -> new ApplyCounter("log-level-info");
            case 3 -> new ApplyCounter("vertical-scale-cpu", "maintenance");
            case 4 -> new ApplyCounter("create-index", "concurrently");
            case 5 -> new ApplyCounter("reschedule-job");
            default -> throw new IllegalArgumentException("no threat in wave " + wave);
        };
    }

    /**
     * The fix bought under fire, often the opposite of the right choice in preparation. Wave 3 takes the live
     * migration: the cheap upgrade's cut-over would fall on the peak. Wave 4 takes the blocking index: 10 s of
     * write lock beat 40 s of a pool that is already exhausted.
     */
    private static Command lateFix(int wave) {
        return switch (wave) {
            case 3 -> new ApplyCounter("vertical-scale-cpu", "live");
            case 4 -> new ApplyCounter("create-index", "blocking");
            default -> fix(wave);
        };
    }

    /**
     * The incident action that buys time while the shield burns. Wave 4 has none: cancelling queries
     * fails them as well, and a statement timeout only turns pool waits into timeouts.
     */
    private static List<Command> mitigate(int wave, GameState s, Observation o) {
        if (o.burnRate() < 1) {
            return List.of();
        }
        return switch (wave) {
            case 2 -> disk(s) >= 0.95 * 100 ? List.of(new RunAction("clear-disk", "truncate")) : List.of();
            case 3 -> List.of(new RunAction("kill-switch"));
            case 5 -> List.of(new RunAction("cancel-queries"));
            default -> List.of();
        };
    }

    interface Policy {
        List<Command> at(GameState s, Observation o);
    }

    private static double disk(GameState s) {
        return ((FillState) s.resource(ResourceId.DISK).state()).level();
    }

    private static GameState idle(GameState s, double seconds) {
        for (int i = 0; i < Math.round(seconds / SimConstants.DT); i++) {
            s = GAME.step(s, List.of(), s.tick() + 1);
        }
        return s;
    }

    /** Commands the game refuses right now (cooling down, already installed, no crew) are dropped, as a client would. */
    private static List<Command> allowed(GameState s, List<Command> wanted) {
        List<Command> ok = new ArrayList<>();
        for (Command c : wanted) {
            try {
                GAME.step(s, List.of(c), s.tick() + 1);
                ok.add(c);
            } catch (IllegalArgumentException refused) {
                // not now
            }
        }
        return ok;
    }

    private static GameState play(GameState s, int wave, Policy policy) {
        s = GAME.step(s, List.of(new StartWave(wave)), s.tick() + 1);
        while (s.wave().phase() == WaveState.Phase.RUNNING) {
            s = GAME.step(s, allowed(s, policy.at(s, GAME.observe(s))), s.tick() + 1);
        }
        if (s.progress().postmortem() != null) {
            s = GAME.step(s, List.of(new AnswerPostmortem(RIGHT[wave])), s.tick() + 1);
        }
        return s;
    }

    private static GameState prepare(GameState s, int wave) {
        return idle(GAME.step(s, List.of(fix(wave)), s.tick() + 1), 45);
    }

    /** The station just before {@code wave}: monitored, every earlier threat fixed ahead of its wave. */
    private static GameState before(int wave) {
        GameState s = GAME.newState(1);
        // Monitoring first: some fixes need what it reveals (create-index needs pg_stat_statements).
        s = idle(GAME.step(s, List.of(new ApplyCounter("prometheus")), s.tick() + 1), 6);
        s = idle(GAME.step(s, List.of(new ApplyCounter("postgres-exporter"), new ApplyCounter("grafana")), s.tick() + 1), 6);
        for (int w = 1; w < wave; w++) {
            if (w >= 2) {
                s = prepare(s, w);
            }
            s = play(s, w, (st, o) -> List.of());
        }
        return s;
    }

    private static double shield(GameState s) {
        Observation o = GAME.observe(s);
        return o.results().get(o.results().size() - 1).shield();
    }

    static double unprepared(int wave) {
        return shield(play(before(wave), wave, (s, o) -> List.of()));
    }

    static double reactive(int wave) {
        return reactive(wave, lateFix(wave));
    }

    static double reactive(int wave, Command lateFix) {
        return shield(play(before(wave), wave, (s, o) -> {
            List<Command> c = new ArrayList<>(mitigate(wave, s, o));
            // The symptom the player reacts to: the shield visibly burning (the HUD shows it from x0.1).
            if (o.burnRate() >= 1) {
                c.add(lateFix);
            }
            return c;
        }));
    }

    static double prepared(int wave) {
        return shield(play(prepare(before(wave), wave), wave, (s, o) -> List.of()));
    }

    @ParameterizedTest(name = "wave {0}")
    @ValueSource(ints = {2, 4, 5})
    void anUnpreparedWaveHurtsButLeavesSomeShield(int wave) {
        assertThat(unprepared(wave)).isBetween(0.2, 0.6);
    }

    /**
     * The festival is the exception (ADR-0018 BA, BB): above 100 % CPU the accept queue fills and most requests
     * fail, so an unplanned surge empties the shield. It is announced a wave ahead; capacity is planned, not
     * improvised. The cheap upgrade bought at the peak makes it worse, its cut-over is downtime at full traffic.
     */
    @Test
    void theFestivalPunishesImprovisedCapacity() {
        assertThat(unprepared(3)).isLessThan(0.2);
        assertThat(reactive(3, new ApplyCounter("vertical-scale-cpu", "maintenance"))).isLessThan(0.2);
    }

    @ParameterizedTest(name = "wave {0}")
    @ValueSource(ints = {2, 3, 4, 5})
    void theRightActionAndALateFixSaveTheWave(int wave) {
        assertThat(reactive(wave)).isGreaterThanOrEqualTo(0.7);
    }

    @ParameterizedTest(name = "wave {0}")
    @ValueSource(ints = {2, 3, 4, 5})
    void aPreparedWaveKeepsTheShield(int wave) {
        assertThat(prepared(wave)).isGreaterThan(0.99);
    }
}
