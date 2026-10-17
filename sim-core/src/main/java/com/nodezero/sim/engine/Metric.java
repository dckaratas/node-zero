package com.nodezero.sim.engine;

import com.nodezero.sim.model.FillState;
import com.nodezero.sim.model.FlowLoad;
import com.nodezero.sim.model.HoldLoad;
import com.nodezero.sim.model.OccupancyState;
import com.nodezero.sim.model.UtilizationState;
import com.nodezero.sim.state.GameState;
import com.nodezero.sim.state.ResourceId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * Everything the player can ever see, named like the real exporters (Micrometer, postgres_exporter)
 * and gated by which monitoring modules are running (fog of war, ADR-0003 decision D).
 * Content may only reference metrics listed here.
 */
public enum Metric {
    REQUEST_RATE(Tier.PROMETHEUS, s -> hold(s, ResourceId.THREADS).arrivalRps()),
    CPU_UTILIZATION(Tier.PROMETHEUS, s -> util(s, ResourceId.CPU).rho()),
    TOMCAT_THREADS_BUSY(Tier.PROMETHEUS, s -> occupancy(s, ResourceId.THREADS).active()),
    HIKARICP_CONNECTIONS_ACTIVE(Tier.PROMETHEUS, s -> occupancy(s, ResourceId.DB_POOL).active()),
    HIKARICP_CONNECTIONS_PENDING(Tier.PROMETHEUS, s -> occupancy(s, ResourceId.DB_POOL).pending()),
    DISK_USAGE_RATIO(Tier.PROMETHEUS, s -> disk(s).fillRatio()),
    DISK_FILL_RATE(Tier.PROMETHEUS, Metric::diskFillRate),
    DB_CPU_UTILIZATION(Tier.POSTGRES_EXPORTER, s -> util(s, ResourceId.DB_CPU).rho()),
    DB_IO_UTILIZATION(Tier.POSTGRES_EXPORTER, s -> util(s, ResourceId.DB_IO).rho()),
    LATENCY_AVG(Tier.GRAFANA, Metric::latencyAvgMs),
    /** Exponential-tail approximation: p99 = ln(100) x mean. Not exact; see ADR-0013. */
    LATENCY_P99(Tier.GRAFANA, s -> latencyAvgMs(s) * Holder.P99_FACTOR),
    ERROR_RATE(Tier.GRAFANA, Metric::errorRate),
    /** Seconds until the disk is full at the current rate; -1 when it is not filling. */
    DISK_FORECAST(Tier.GRAFANA, s -> {
        double seconds = disk(s).secondsToFull();
        return Double.isInfinite(seconds) ? -1 : seconds;
    }),
    /** Arrival rate as the heatmap panel shows it; per-colony split comes with colonies. */
    TRAFFIC_HEATMAP(Tier.GRAFANA, s -> hold(s, ResourceId.THREADS).arrivalRps()),
    /** Mean query time from pg_stat_statements, shown on a dashboard. */
    SLOW_QUERIES(Tier.GRAFANA_POSTGRES, s -> hold(s, ResourceId.DB_POOL).holdTimeMs());

    /** Which monitoring modules a metric needs. Higher tiers include the lower ones they build on. */
    public enum Tier {
        PROMETHEUS(true, false, false),
        POSTGRES_EXPORTER(true, true, false),
        GRAFANA(true, false, true),
        GRAFANA_POSTGRES(true, true, true);

        final boolean needsPrometheus;
        final boolean needsExporter;
        final boolean needsGrafana;

        Tier(boolean needsPrometheus, boolean needsExporter, boolean needsGrafana) {
            this.needsPrometheus = needsPrometheus;
            this.needsExporter = needsExporter;
            this.needsGrafana = needsGrafana;
        }

        /** Raw exporter metrics are listed under {@code prometheus:} in content, panels under {@code grafana:}. */
        public boolean isPanel() {
            return needsGrafana;
        }
    }

    private final Tier tier;
    private final ToDoubleFunction<GameState> reader;
    private final String metricName;

    Metric(Tier tier, ToDoubleFunction<GameState> reader) {
        this.tier = tier;
        this.reader = reader;
        this.metricName = name().toLowerCase(Locale.ROOT);
    }

    public Tier tier() {
        return tier;
    }

    /** The name as content and the client use it, e.g. {@code hikaricp_connections_pending}. */
    public String metricName() {
        return metricName;
    }

    double read(GameState s) {
        return reader.applyAsDouble(s);
    }

    /** @return the metric with this name, or {@code null} */
    public static Metric byName(String name) {
        return Holder.BY_NAME.get(name);
    }

    private static double latencyAvgMs(GameState s) {
        return occupancy(s, ResourceId.THREADS).waitMs() + hold(s, ResourceId.THREADS).holdTimeMs();
    }

    private static double errorRate(GameState s) {
        double rate = hold(s, ResourceId.THREADS).arrivalRps();
        return rate > 0 ? Math.min(1, s.wave().lastFailedRps() / rate) : 0;
    }

    private static double diskFillRate(GameState s) {
        FlowLoad load = (FlowLoad) s.resource(ResourceId.DISK).load();
        return load.inflowPerSecond() - load.drainPerSecond();
    }

    private static UtilizationState util(GameState s, ResourceId id) {
        return (UtilizationState) s.resource(id).state();
    }

    private static OccupancyState occupancy(GameState s, ResourceId id) {
        return (OccupancyState) s.resource(id).state();
    }

    private static HoldLoad hold(GameState s, ResourceId id) {
        return (HoldLoad) s.resource(id).load();
    }

    private static FillState disk(GameState s) {
        return (FillState) s.resource(ResourceId.DISK).state();
    }

    /** Static data initialised after the constants. */
    private static final class Holder {
        static final double P99_FACTOR = StrictMath.log(100);
        static final Map<String, Metric> BY_NAME = new LinkedHashMap<>();

        static {
            for (Metric m : values()) {
                BY_NAME.put(m.metricName, m);
            }
        }
    }
}
