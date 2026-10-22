package com.nodezero.server.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import com.nodezero.sim.engine.Observation;
import com.nodezero.sim.state.WaveState;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SnapshotStreamTest {

    private final SnapshotStream stream = new SnapshotStream(10);

    private static Observation view(long tick, double cpu, List<String> counterIds) {
        List<Observation.Counter> counters = counterIds.stream().map(id -> new Observation.Counter(id, null, 0, -1, null)).toList();
        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("node_cpu_utilization", cpu);
        return new Observation(tick, 1000, 0, WaveState.Phase.PREPARATION, 1.0, 0, -1, metrics,
                List.of(), List.of(), counters, null, List.of(), List.of(), null, 8, List.of());
    }

    @Test
    void firstSnapshotIsAKeyframeWithEveryField() {
        ServerMessage.Snapshot first = stream.next(view(2, 0.5, List.of("prometheus")));

        assertThat(first).isEqualTo(ServerMessage.Snapshot.full(view(2, 0.5, List.of("prometheus"))));
    }

    @Test
    void anUnchangedViewIsNotSentAgain() {
        stream.next(view(2, 0.5, List.of()));

        assertThat(stream.next(view(4, 0.5, List.of()))).isNull();
        assertThat(stream.next(view(6, 0.5, List.of()))).isNull();
    }

    @Test
    void changedMetricsGoOutWithoutTheUnchangedLists() {
        stream.next(view(2, 0.5, List.of("prometheus")));

        ServerMessage.Snapshot next = stream.next(view(4, 0.6, List.of("prometheus")));

        assertThat(next.metrics()).containsEntry("node_cpu_utilization", 0.6);
        assertThat(next.counters()).isNull();
        assertThat(next.insights()).isNull();
    }

    @Test
    void aNewCounterSendsTheCounterList() {
        stream.next(view(2, 0.5, List.of()));

        ServerMessage.Snapshot next = stream.next(view(4, 0.5, List.of("prometheus")));

        assertThat(next.counters()).extracting(Observation.Counter::id).containsExactly("prometheus");
        assertThat(next.insights()).isNull();
    }

    @Test
    void aKeyframeGoesOutEachIntervalEvenWhenNothingChanged() {
        stream.next(view(2, 0.5, List.of("prometheus")));
        assertThat(stream.next(view(10, 0.5, List.of("prometheus")))).isNull();

        ServerMessage.Snapshot heartbeat = stream.next(view(12, 0.5, List.of("prometheus")));

        assertThat(heartbeat.tick()).isEqualTo(12);
        assertThat(heartbeat.counters()).extracting(Observation.Counter::id).containsExactly("prometheus");
        assertThat(heartbeat.insights()).isEmpty();
        assertThat(stream.next(view(14, 0.5, List.of("prometheus")))).isNull();
    }
}
