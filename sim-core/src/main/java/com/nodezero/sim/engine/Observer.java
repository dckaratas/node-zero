package com.nodezero.sim.engine;

import com.nodezero.sim.content.ThreatDef;
import com.nodezero.sim.state.GameState;
import com.nodezero.sim.state.InstalledCounter;
import com.nodezero.sim.state.Progress;
import com.nodezero.sim.state.WaveState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the full simulation state into what a player may see (fog of war, decision D).
 * The simulation always computes everything; only this view is filtered, on the server.
 */
final class Observer {

    /** Users complain after this long of continuous failures in a wave without a threat of its own. */
    private static final double DEFAULT_COMPLAINT_DELAY_S = 30;
    private static final String DEFAULT_SIGNAL = "user_complaints";

    private final Rules rules;

    Observer(Rules rules) {
        this.rules = rules;
    }

    Observation observe(GameState s) {
        Progress p = s.progress();
        WaveState wave = p.wave();
        Modules modules = modules(p, s.tick());

        Map<String, Double> metrics = new LinkedHashMap<>();
        for (Metric metric : Metric.values()) {
            if (modules.allow(metric.tier())) {
                metrics.put(metric.metricName(), metric.read(s));
            }
        }

        List<String> signals = new ArrayList<>();
        if (wave.phase() == WaveState.Phase.RUNNING && wave.symptomSinceTick() >= 0) {
            ThreatDef threat = rules.threatForWave(wave.number());
            String signal = threat == null ? DEFAULT_SIGNAL : threat.visibility().withoutMonitoring().signal();
            double delayS = threat == null ? DEFAULT_COMPLAINT_DELAY_S : threat.visibility().withoutMonitoring().delayS();
            if ((s.tick() - wave.symptomSinceTick()) * SimConstants.DT >= delayS) {
                signals.add(signal);
            }
        }

        List<Observation.Counter> counters = new ArrayList<>();
        p.counters().forEach(c -> counters.add(new Observation.Counter(c.id(), c.option(), c.activeTick())));
        return new Observation(s.tick(), p.credits(), wave.number(), wave.phase(),
                wave.shield(rules.balance().slo()), metrics, signals, p.insights(), counters);
    }

    private Modules modules(Progress p, long tick) {
        boolean prometheus = false;
        boolean exporter = false;
        boolean grafana = false;
        for (InstalledCounter installed : p.counters()) {
            if (!installed.isActive(tick)) {
                continue;
            }
            for (Rules.Effect e : rules.counter(installed.id()).variant(installed.option()).effects()) {
                switch (e.kind()) {
                    case ENABLE_PROMETHEUS -> prometheus = true;
                    case ENABLE_POSTGRES_EXPORTER -> exporter = true;
                    case ENABLE_GRAFANA -> grafana = true;
                    default -> { }
                }
            }
        }
        return new Modules(prometheus, exporter, grafana);
    }

    private record Modules(boolean prometheus, boolean exporter, boolean grafana) {
        boolean allow(Metric.Tier tier) {
            return (!tier.needsPrometheus || prometheus)
                    && (!tier.needsExporter || exporter)
                    && (!tier.needsGrafana || grafana);
        }
    }
}
