package com.nodezero.sim.engine;

import static com.nodezero.sim.state.ResourceId.CPU;
import static com.nodezero.sim.state.ResourceId.DB_CPU;
import static com.nodezero.sim.state.ResourceId.DB_IO;
import static com.nodezero.sim.state.ResourceId.DB_POOL;
import static com.nodezero.sim.state.ResourceId.DISK;
import static com.nodezero.sim.state.ResourceId.THREADS;

import com.nodezero.sim.content.ThreatDef;
import com.nodezero.sim.model.Demand;
import com.nodezero.sim.model.HoldLoad;
import com.nodezero.sim.model.OccupancyModel;
import com.nodezero.sim.model.OccupancyState;
import com.nodezero.sim.model.UtilizationModel;
import com.nodezero.sim.model.UtilizationState;
import com.nodezero.sim.state.GameState;
import com.nodezero.sim.state.ResourceId;
import com.nodezero.sim.state.ResourceNode;
import java.util.EnumMap;
import java.util.Map;

/**
 * Request path of NODE-01: Tomcat threads → CPU → HikariCP → DB CPU / DB I/O, plus logs to disk.
 *
 * <p>Arrival rates flow downstream within the tick. Latencies flow upstream with a one-tick lag:
 * a thread's hold time is built from the CPU, pool and query latencies of the previous tick. The lag
 * (100 ms) breaks the cycle "hold time depends on latency depends on load" without iteration, which
 * keeps the tick cheap and deterministic. This coupling is what makes a slow query saturate the
 * pools while app CPU stays low (wave 4).
 *
 * <p>Hot path (ADR-0014): no streams, regex, strings or logging; one small EnumMap per tick.
 */
final class Pipeline implements LoadPlanner {

    /** Share of the gap to the new hold time closed per tick. */
    private static final double DAMPING = 0.5;

    private final Rules rules;
    private final ThreatDef[] threatByWave;

    Pipeline(Rules rules) {
        this.rules = rules;
        int maxWave = 0;
        for (ThreatDef t : rules.content().threats().values()) {
            maxWave = Math.max(maxWave, t.trigger().wave());
        }
        threatByWave = new ThreatDef[maxWave + 1];
        for (int wave = 1; wave <= maxWave; wave++) {
            threatByWave[wave] = rules.threatForWave(wave);
        }
    }

    @Override
    public Map<ResourceId, Demand> plan(GameState s, long tick) {
        StationLayout station = rules.station();
        int wave = s.wave().number();
        ThreatDef threat = wave < threatByWave.length ? threatByWave[wave] : null;
        double seconds = (tick - s.wave().startTick()) * SimConstants.DT;

        double arrivals = station.baselineRps();
        double writeFraction = station.writeFraction();
        double logUnits = station.logUnitsPerRequest();
        double cpuCost = 1;
        double dbCpuCost = 1;
        double dbIoCost = 1;
        if (threat != null) {
            arrivals = TrafficShape.rateAt(threat.traffic(), seconds);
            writeFraction = threat.traffic().mix().write();
            double rows = threat.data() == null ? 0 : threat.data().initialRows() + threat.data().rowsPerSecond() * seconds;
            for (ThreatDef.Consumption c : threat.consumes()) {
                switch (c.model()) {
                    case UTILIZATION -> {
                        double factor = costFactor(s, c, rows);
                        if (c.resource() == CPU) {
                            cpuCost = factor;
                        } else if (c.resource() == DB_CPU) {
                            dbCpuCost = factor;
                        } else if (c.resource() == DB_IO) {
                            dbIoCost = factor;
                        }
                    }
                    case FILL -> logUnits = c.unitsPerRequest();
                    case OCCUPANCY -> { } // background jobs holding slots: slice 3b
                }
            }
        }

        // Thread hold from last tick's latencies, damped against last tick's hold to avoid oscillation.
        double threadHoldMs = damped(s, THREADS,
                latencyMs(s, CPU, cpuCost) + occupancy(s, DB_POOL).waitMs() + previousHoldMs(s, DB_POOL));

        // A pool forwards at most what its slots can cycle (closed loop): a saturated pool queues
        // requests instead of overloading the tier behind it.
        double accepted = Math.max(0, arrivals - occupancy(s, THREADS).rejectedRps());
        double toCpu = Math.min(accepted, throughputRps(s, THREADS, threadHoldMs));
        double toPool = Math.max(0, toCpu - occupancy(s, DB_POOL).rejectedRps());

        // Database tier in the same tick: with only `connections` queries in flight, the database never
        // sees more than its capacity and a query never waits longer than connections / throughput.
        UtilizationModel db = utilizationModel(s, DB_CPU);
        double toDatabase = Math.min(toPool, db.maxSteadyRps(dbCpuCost));
        double dbLatencyMs = db.serviceTimeMs() * dbCpuCost;
        if (toDatabase > 0) {
            double closedBoundMs = 1000.0 * poolSize(s, DB_POOL) / toDatabase;
            dbLatencyMs = Math.min(db.steadyLatencyMs(toDatabase, dbCpuCost), Math.max(dbLatencyMs, closedBoundMs));
        }
        double queryMs = dbLatencyMs + writeFraction * latencyMs(s, DB_IO, dbIoCost);

        EnumMap<ResourceId, Demand> demands = new EnumMap<>(ResourceId.class);
        demands.put(THREADS, Demand.hold(arrivals, threadHoldMs));
        demands.put(CPU, Demand.rate(toCpu, cpuCost));
        demands.put(DB_POOL, Demand.hold(toPool, queryMs));
        demands.put(DB_CPU, Demand.rate(toDatabase, dbCpuCost));
        demands.put(DB_IO, Demand.rate(toDatabase * writeFraction, dbIoCost));
        demands.put(DISK, Demand.flow(accepted, logUnits, 0));
        return demands;
    }

    /** Threat service time relative to the station's baseline for that resource. */
    private static double costFactor(GameState s, ThreatDef.Consumption c, double rows) {
        double ms = c.serviceTimeMs() + (c.perRowMs() == null ? 0 : c.perRowMs() * rows);
        return ms / utilizationModel(s, c.resource()).serviceTimeMs();
    }

    /** Previous tick's latency; before any load, the (cost-adjusted) service time. */
    private static double latencyMs(GameState s, ResourceId id, double costFactor) {
        double latency = ((UtilizationState) s.resource(id).state()).latencyMs();
        return latency > 0 ? latency : utilizationModel(s, id).serviceTimeMs() * costFactor;
    }

    /** Exponential smoothing against last tick's hold time; a first-order lag of about 2 ticks. */
    private static double damped(GameState s, ResourceId pool, double targetMs) {
        double previous = previousHoldMs(s, pool);
        return previous <= 1 ? targetMs : previous + DAMPING * (targetMs - previous);
    }

    /** Last tick's hold time of a pool; 1 ms before the pool ever saw load. */
    private static double previousHoldMs(GameState s, ResourceId pool) {
        return ((HoldLoad) s.resource(pool).load()).holdTimeMs();
    }

    /** Requests per second a pool's slots can complete at the given hold time. */
    private static double throughputRps(GameState s, ResourceId pool, double holdMs) {
        return poolSize(s, pool) * 1000.0 / holdMs;
    }

    private static int poolSize(GameState s, ResourceId pool) {
        return ((OccupancyModel) s.resource(pool).model()).poolSize();
    }

    private static OccupancyState occupancy(GameState s, ResourceId id) {
        return (OccupancyState) s.resource(id).state();
    }

    private static UtilizationModel utilizationModel(GameState s, ResourceId id) {
        ResourceNode<?, ?> node = s.resource(id);
        return (UtilizationModel) node.model();
    }
}
