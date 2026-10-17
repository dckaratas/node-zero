package com.nodezero.bench;

import com.nodezero.sim.engine.Command;
import com.nodezero.sim.engine.Game;
import com.nodezero.sim.engine.GameBalance;
import com.nodezero.sim.state.GameState;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Tick + observe across many live sessions, the way a shard runs them: each session's state is
 * replaced every tick, so with thousands of them the work runs on cold caches and fresh garbage.
 * Score is per session-tick; compare {@code sessions=1} (hot) with a shard-sized population.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class ManySessionsBenchmark {

    @Param({"1", "500"})
    public int sessions;

    private Game game;
    private GameState[] states;

    @Setup
    public void setUp() {
        game = Game.chapterOne(GameBalance.PRACTICE);
        states = new GameState[sessions];
        for (int i = 0; i < sessions; i++) {
            GameState s = game.newState(i);
            s = game.step(s, List.of(new Command.ApplyCounter("prometheus")), 1);
            s = game.step(s, List.of(new Command.ApplyCounter("grafana")), 2);
            for (long tick = 3; tick <= 300; tick++) {
                s = game.step(s, tick == 100 && i % 3 == 0 ? List.of(new Command.StartWave(3)) : List.of(), tick);
            }
            states[i] = s;
        }
    }

    /** One shard loop; the score divided by {@code sessions} is the per-session cost. */
    @Benchmark
    public void shardLoop(Blackhole bh) {
        for (int i = 0; i < states.length; i++) {
            GameState s = game.step(states[i], List.of(), states[i].tick() + 1);
            states[i] = s;
            bh.consume(game.observe(s));
        }
    }
}
