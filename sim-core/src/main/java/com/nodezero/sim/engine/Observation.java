package com.nodezero.sim.engine;

import com.nodezero.sim.state.WaveState;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What one player is allowed to see at a tick: the server-side fog-of-war view (decision D).
 * Built from {@link com.nodezero.sim.state.GameState} by {@link Game#observe}; never contains threat
 * ids, root causes or metrics the player's monitoring cannot measure. Slice 4 serialises this.
 *
 * @param shield error budget left in the running wave, in [0, 1]; always visible, like hit points
 * @param metrics visible metrics in catalog order ({@link Metric}), by exporter name
 * @param signals what users report without any monitoring, e.g. {@code user_complaints}
 * @param insights diagnostic capabilities gained (e.g. {@code pg-stat-statements})
 * @param counters installed counters, in purchase order
 */
public record Observation(
        long tick,
        int credits,
        int wave,
        WaveState.Phase phase,
        double shield,
        Map<String, Double> metrics,
        List<String> signals,
        List<String> insights,
        List<String> counters) {

    public Observation {
        metrics = Collections.unmodifiableMap(new LinkedHashMap<>(metrics));
        signals = List.copyOf(signals);
        insights = List.copyOf(insights);
        counters = List.copyOf(counters);
    }
}
