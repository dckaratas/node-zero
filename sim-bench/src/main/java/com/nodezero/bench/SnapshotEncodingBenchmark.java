package com.nodezero.bench;

import com.nodezero.server.protocol.ServerMessage;
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
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cost of turning one fully monitored observation into the bytes a WebSocket frame carries
 * (ADR-0015 finding 1). Mid-festival state, so every metric is present.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class SnapshotEncodingBenchmark {

    private JsonMapper json;
    private ObjectWriter writer;
    private Observation observation;

    @Setup
    public void setUp() {
        Game game = Game.chapterOne(GameBalance.PRACTICE);
        GameState s = game.newState(42);
        s = game.step(s, List.of(new Command.ApplyCounter("prometheus")), 1);
        s = game.step(s, List.of(new Command.ApplyCounter("postgres-exporter")), 2);
        s = game.step(s, List.of(new Command.ApplyCounter("grafana")), 3);
        for (long tick = 4; tick <= 700; tick++) {
            s = game.step(s, tick == 100 ? List.of(new Command.StartWave(3)) : List.of(), tick);
        }
        observation = game.observe(s);
        json = JsonMapper.builder().build();
        writer = json.writerFor(ServerMessage.class);
    }

    /** What the server does: Spring sends text frames as a String (sendText), so this is the real path. */
    @Benchmark
    public String mapperToString() {
        return json.writeValueAsString(ServerMessage.Snapshot.full(observation));
    }

    /** Measured and rejected: not faster, and Spring would decode the bytes back to a String. */
    @Benchmark
    public byte[] mapperToBytes() {
        return json.writeValueAsBytes(ServerMessage.Snapshot.full(observation));
    }

    /** Prebuilt writer straight to UTF-8 bytes. */
    @Benchmark
    public byte[] writerToBytes() {
        return writer.writeValueAsBytes(ServerMessage.Snapshot.full(observation));
    }
}
