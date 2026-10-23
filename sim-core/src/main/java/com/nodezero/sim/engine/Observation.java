package com.nodezero.sim.engine;

import com.nodezero.sim.state.ActionRun;
import com.nodezero.sim.state.AlertRule;
import com.nodezero.sim.state.WaveResult;
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
 * @param burnRate how fast the budget burns compared with the pace that would last the whole wave
 *     (SRE burn rate: 1 = empty exactly at the end); 0 outside a running wave
 * @param shieldEmptyInS seconds until the shield breaks at the current failure rate, or -1 when it is not burning
 * @param metrics visible metrics in catalog order ({@link Metric}), by exporter name
 * @param signals what users report without any monitoring, e.g. {@code user_complaints}
 * @param insights diagnostic capabilities gained (e.g. {@code pg-stat-statements})
 * @param counters installed counters in purchase order, including ones still being built
 * @param postmortem the open or just-answered postmortem, or {@code null}
 * @param codex codex entries the player has opened (ids; texts come from the server by language)
 * @param results how each played wave ended (slice 7a)
 * @param chapter the chapter summary once its last wave is won and reviewed, otherwise {@code null}
 * @param crew crew points left in this shift (slice 7b)
 * @param actions latest run of each incident action used, with its cooldown
 * @param alerts the player's alert rules with their state (slice 7d)
 * @param station the station's capacities (slice 8a): the player's own hardware, so never hidden by the fog
 */
public record Observation(
        long tick,
        int credits,
        int wave,
        WaveState.Phase phase,
        double shield,
        double burnRate,
        double shieldEmptyInS,
        Map<String, Double> metrics,
        List<String> signals,
        List<String> insights,
        List<Counter> counters,
        Postmortem postmortem,
        List<String> codex,
        List<WaveResult> results,
        Chapter chapter,
        int crew,
        List<ActionRun> actions,
        List<AlertRule> alerts,
        Station station,
        Briefing briefing) {

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

    /**
     * The chapter summary (decision AE): every wave survived, every postmortem answered.
     * One star for finishing, one for an average shield of at least half, one for every root cause right.
     */
    public record Chapter(int number, int stars, double averageShield, int postmortemsRight, int postmortems) {}

    /**
     * What NODE-01 is built of right now; structural counters change it (more cores, threads, a bigger disk).
     * Lets the map show load against the real capacity instead of a constant in the client.
     * {@code appDown} is true while the app is stopped (a restart, a rollback, a CPU swap): the player
     * caused it, so the map may show it without monitoring (slice 8b, decision AP).
     */
    public record Station(int cpuCores, int threads, int dbPool, double diskMb, int dbCpuCores, int dbIoDisks,
            boolean appDown) {}

    /**
     * What the player is told about the next wave (slice 9c, decisions CA/CB): the event, never the root cause,
     * and station comms announced for it (the festival teaser). Texts are keys. While a wave runs it describes
     * that wave; {@code null} once the chapter has no wave left.
     */
    public record Briefing(int wave, String eventKey, List<String> comms) {

        public Briefing {
            comms = List.copyOf(comms);
        }
    }

    /** One answer option; {@code key} is its text key. */
    public record Choice(String id, String key) {}

    /**
     * An installed counter as the player sees it. It works from {@code readyTick} on; before that it is
     * being built (the client shows progress as {@code (readyTick - tick) * DT} seconds left).
     *
     * @param option chosen option id, or {@code null}
     * @param removedTick tick its removal completes, or -1 (slice 7c)
     * @param setting the value of its driving setting when the player changed it, otherwise {@code null}
     */
    public record Counter(String id, String option, long readyTick, long removedTick, Double setting) {

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
        results = List.copyOf(results);
        actions = List.copyOf(actions);
        alerts = List.copyOf(alerts);
    }
}
