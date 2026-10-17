package com.nodezero.bench;

import com.nodezero.sim.engine.Command;
import com.nodezero.sim.engine.Game;
import com.nodezero.sim.engine.GameBalance;
import com.nodezero.sim.engine.Observation;
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

/**
 * Cost of one game tick for one session (ADR-0014 budget: under 20 us).
 * Always steps the same mid-wave state, so every invocation measures the same work.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class GameStepBenchmark {

    /** 0 = quiet station; 3 = festival (CPU saturated); 4 = slow query (pools saturated). */
    @Param({"0", "3", "4"})
    public int wave;

    private Game game;
    private GameState midWave;

    @Setup
    public void setUp() {
        game = Game.chapterOne(GameBalance.PRACTICE);
        GameState s = game.newState(42);
        // Full monitoring, so observe() builds every metric.
        s = game.step(s, List.of(new Command.ApplyCounter("prometheus")), 1);
        s = game.step(s, List.of(new Command.ApplyCounter("postgres-exporter")), 2);
        s = game.step(s, List.of(new Command.ApplyCounter("grafana")), 3);
        for (long tick = 4; tick <= 700; tick++) {
            s = game.step(s, tick == 100 && wave > 0 ? List.of(new Command.StartWave(wave)) : List.of(), tick);
        }
        midWave = s;
    }

    @Benchmark
    public GameState step() {
        return game.step(midWave, List.of(), midWave.tick() + 1);
    }

    /** Fog-of-war view a session sends with each snapshot (slice 4). */
    @Benchmark
    public Observation observe() {
        return game.observe(midWave);
    }
}
