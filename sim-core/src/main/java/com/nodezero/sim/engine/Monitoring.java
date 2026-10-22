package com.nodezero.sim.engine;

import com.nodezero.sim.state.InstalledCounter;
import com.nodezero.sim.state.Progress;

/**
 * Which monitoring modules are running at a tick, and so which metrics the player can measure
 * (fog of war, decision D). Shared by the view ({@link Observer}) and alert rules ({@link Alerts}):
 * an alert can only watch what a dashboard could show.
 */
record Monitoring(boolean prometheus, boolean exporter, boolean grafana) {

    static Monitoring at(Rules rules, Progress p, long tick) {
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
        return new Monitoring(prometheus, exporter, grafana);
    }

    boolean allow(Metric.Tier tier) {
        return (!tier.needsPrometheus || prometheus)
                && (!tier.needsExporter || exporter)
                && (!tier.needsGrafana || grafana);
    }
}
