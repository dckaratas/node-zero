package com.nodezero.server.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.nodezero.server.ServerProperties;
import com.nodezero.server.protocol.ServerMessage;
import com.nodezero.sim.engine.Command;
import com.nodezero.sim.engine.Game;
import com.nodezero.sim.engine.Observation;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.databind.json.JsonMapper;

/**
 * Opt-in capacity check (ADR-0014/0015): many sessions on the real shard scheduler, each tick
 * stepping the game and serialising its observation to JSON as the WebSocket layer would.
 * Run: {@code ./mvnw test -pl game-server -am -Dsurefire.failIfNoSpecifiedTests=false
 * -Dtest=SchedulerLoadTest -Dnodezero.load=true -Dnodezero.load.sessions=4000}
 */
@EnabledIfSystemProperty(named = "nodezero.load", matches = "true")
class SchedulerLoadTest {

    @Test
    void shardLoopsStayWithinTheTick() throws InterruptedException {
        int sessions = Integer.getInteger("nodezero.load.sessions", 2000);
        Game game = Game.chapterOne();
        JsonMapper json = JsonMapper.builder().build();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ServerProperties props = new ServerProperties(0, sessions, 1000, 524288, List.of("*"));
        SessionHost host = new SessionHost(game, props, registry);
        AtomicLong bytes = new AtomicLong();

        SessionListener sink = new SessionListener() {
            @Override
            public void onObservation(Observation observation) {
                bytes.addAndGet(json.writeValueAsString(new ServerMessage.Snapshot(observation)).length());
            }

            @Override
            public void onRejected(Command command, String reason) {}

            @Override
            public void onPauseChanged(boolean paused) {}

            @Override
            public void onFailure(Throwable error) {
                throw new AssertionError(error);
            }
        };
        for (int i = 0; i < sessions; i++) {
            Session s = host.open(sink);
            // A realistic mix: monitoring bought (bigger observations), a third of them mid-festival.
            s.submit(new Command.ApplyCounter("prometheus"));
            s.submit(new Command.ApplyCounter("grafana"));
            if (i % 3 == 0) {
                s.submit(new Command.StartWave(3));
            }
        }

        host.start();
        TimeUnit.SECONDS.sleep(3); // warm-up: JIT, first ticks with commands
        List<Timer> timers = List.copyOf(registry.find("nodezero.tick.loop").timers());
        long[] countsBefore = timers.stream().mapToLong(Timer::count).toArray();
        double[] totalsBefore = timers.stream().mapToDouble(t -> t.totalTime(TimeUnit.MILLISECONDS)).toArray();
        bytes.set(0);
        TimeUnit.SECONDS.sleep(5);
        host.stop();

        double worstMeanMs = 0;
        double maxMs = 0;
        long loops = 0;
        for (int i = 0; i < timers.size(); i++) {
            Timer t = timers.get(i);
            long count = t.count() - countsBefore[i];
            double mean = (t.totalTime(TimeUnit.MILLISECONDS) - totalsBefore[i]) / Math.max(1, count);
            worstMeanMs = Math.max(worstMeanMs, mean);
            maxMs = Math.max(maxMs, t.max(TimeUnit.MILLISECONDS));
            loops += count;
        }
        System.out.printf(Locale.ROOT,
                "LOAD sessions=%d shards=%d loops=%d worstShardMean=%.2fms max=%.2fms json=%.1f MB/s%n",
                sessions, props.effectiveShards(), loops, worstMeanMs, maxMs, bytes.get() / 5.0 / 1_000_000);
        assertThat(worstMeanMs).isLessThan(100);
    }
}
