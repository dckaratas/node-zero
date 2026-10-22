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
 * @param counters installed counters in purchase order, including ones still being built
 * @param postmortem the open or just-answered postmortem, or {@code null}
 * @param codex codex entries the player has opened (ids; texts come from the server by language)
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
        List<Counter> counters,
        Postmortem postmortem,
        List<String> codex) {

    /**
     * A root-cause question after a threat wave (ADR-0007/0008). Texts are keys (ADR-0009).
     * {@code correct} stays {@code null} until the player has answered: the server never sends the
     * answer ahead (fog of war, decision Z).
     */
    public record Postmortem(int wave, String questionKey, List<Choice> options, String chosen, String correct) {

        public Postmortem {
            options = List.copyOf(options);
        }
    }

    /** One answer option; {@code key} is its text key. */
    public record Choice(String id, String key) {}

    /**
     * An installed counter as the player sees it. It works from {@code readyTick} on; before that it is
     * being built (the client shows progress as {@code (readyTick - tick) * DT} seconds left).
     *
     * @param option chosen option id, or {@code null}
     */
    public record Counter(String id, String option, long readyTick) {

        public boolean isReady(long tick) {
            return tick >= readyTick;
        }
    }

    public Observation {
        metrics = Collections.unmodifiableMap(new LinkedHashMap<>(metrics));
        signals = List.copyOf(signals);
        insights = List.copyOf(insights);
        counters = List.copyOf(counters);
        codex = List.copyOf(codex);
    }
}
