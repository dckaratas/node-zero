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
import com.nodezero.sim.state.ActiveThreat;
import com.nodezero.sim.state.GameState;
import com.nodezero.sim.state.InstalledCounter;
import com.nodezero.sim.state.Progress;
import com.nodezero.sim.state.ResourceId;
import com.nodezero.sim.state.WaveState;
import java.util.EnumMap;

/**
 * Request path of NODE-01: [Nginx] → Tomcat threads → CPU → HikariCP → DB CPU / DB I/O, plus logs to disk.
 *
 * <p>Arrival rates flow downstream within the tick. Thread hold time is built from last tick's
 * latencies, damped, which breaks the "hold depends on latency depends on load" cycle without
 * iteration. The database tier behind the pool is a closed system and is solved in the same tick.
 *
 * <p>Costs come from the active threats (root causes only); counters modify them (ADR-0005, decision C).
 *
 * <p>Hot path (ADR-0014): no streams, regex, strings or logging; a few small objects per tick.
 */
final class Pipeline {

    /** Share of the gap to the new hold time closed per tick. */
    private static final double DAMPING = 0.5;
    /** One real second is one game minute, so a game day lasts 1440 s. */
    private static final double DAY_SECONDS = 1440;

    private final Rules rules;

    Pipeline(Rules rules) {
        this.rules = rules;
    }

    Plan plan(GameState s, long tick) {
        Progress progress = s.progress();
        WaveState wave = progress.wave();
        StationLayout station = rules.station();
        Modifiers m = modifiers(progress, tick);

        double arrivals = station.baselineRps();
        double writeFraction = station.writeFraction();
        ThreatDef waveThreat = wave.phase() == WaveState.Phase.RUNNING ? rules.threatForWave(wave.number()) : null;
        if (waveThreat != null) {
            arrivals = TrafficShape.rateAt(waveThreat.traffic(), (tick - wave.startTick()) * SimConstants.DT);
            writeFraction = waveThreat.traffic().mix().write();
        }

        // Costs of the active threats.
        double logUnits = station.logUnitsPerRequest();
        double cpuCost = 1;
        double dbCpuCost = 1;
        double dbIoCost = 1;
        int reservedSlots = 0;
        double jobIoRps = 0;
        double jobIoCost = 1;
        for (ActiveThreat active : progress.activeThreats()) {
            ThreatDef t = rules.content().threat(active.id());
            double since = (tick - active.sinceTick()) * SimConstants.DT;
            double rows = t.data() == null ? 0 : t.data().initialRows() + t.data().rowsPerSecond() * since;
            for (ThreatDef.Consumption c : t.consumes()) {
                switch (c.model()) {
                    case UTILIZATION -> {
                        double perRow = c.perRowMs() == null ? 0
                                : (c.resource() == DB_CPU && !Double.isNaN(m.perRowMs) ? m.perRowMs : c.perRowMs());
                        double factor = (c.serviceTimeMs() + perRow * rows) / serviceTimeMs(s, c.resource());
                        if (c.resource() == CPU) {
                            cpuCost = factor;
                        } else if (c.resource() == DB_CPU) {
                            dbCpuCost = factor;
                        } else if (c.resource() == DB_IO) {
                            dbIoCost = factor;
                        }
                    }
                    case FILL -> logUnits = c.unitsPerRequest();
                    case OCCUPANCY -> {
                        if (t.job() != null && jobRunning(t.job(), c, rows, since, m)) {
                            reservedSlots += m.isolatedSlots > 0 ? 0 : c.slots();
                            jobIoRps += t.job().ioRps() * m.jobThrottle;
                            jobIoCost = t.job().ioServiceTimeMs() / serviceTimeMs(s, DB_IO);
                        }
                    }
                }
            }
        }
        if (!Double.isNaN(m.logUnits)) {
            logUnits = m.logUnits;
        }
        dbIoCost *= m.ioLoadFactor;

        // Edge: rate limiter (429s) and Tomcat.
        double admitted = Math.min(arrivals, m.rateLimitRps);
        double edgeFailed = arrivals - admitted;
        double threadHoldMs = damped(s, THREADS,
                latencyMs(s, CPU, cpuCost) + occupancy(s, DB_POOL).waitMs() + previousHoldMs(s, DB_POOL));
        double accepted = Math.max(0, admitted - occupancy(s, THREADS).rejectedRps());
        double toCpu = Math.min(accepted, throughputRps(s, THREADS, threadHoldMs, 0));
        if (m.cpuDown) {
            edgeFailed += toCpu;
            toCpu = 0;
        }

        // Pool and database (closed system: at most `connections` queries in flight).
        double retries = wave.lastFailedRps() > 0 ? m.retryMultiplier : 1;
        double toPool = Math.max(0, toCpu - occupancy(s, DB_POOL).rejectedRps()) * retries;
        int connections = pool(s, DB_POOL).availableSlots(reservedSlots);
        UtilizationModel db = utilizationModel(s, DB_CPU);
        double toDatabase = Math.min(toPool, db.maxSteadyRps(dbCpuCost));
        double dbLatencyMs = db.serviceTimeMs() * dbCpuCost;
        if (toDatabase > 0) {
            double closedBoundMs = 1000.0 * connections / toDatabase;
            dbLatencyMs = Math.min(db.steadyLatencyMs(toDatabase, dbCpuCost), Math.max(dbLatencyMs, closedBoundMs));
        }
        double userIoRps = toDatabase * writeFraction;
        double ioRps = userIoRps + jobIoRps;
        double ioCost = ioRps > 0 ? (userIoRps * dbIoCost + jobIoRps * jobIoCost) / ioRps : dbIoCost;
        double queryMs = dbLatencyMs + writeFraction * latencyMs(s, DB_IO, ioCost);
        if (queryMs > m.holdCapMs) {
            edgeFailed += toDatabase; // statement_timeout cancels the queries
            queryMs = m.holdCapMs;
        }
        if (m.writeLocked) {
            edgeFailed += userIoRps;
        }

        EnumMap<ResourceId, Demand> demands = new EnumMap<>(ResourceId.class);
        demands.put(THREADS, Demand.hold(admitted, threadHoldMs, 0));
        demands.put(CPU, Demand.rate(toCpu, cpuCost));
        demands.put(DB_POOL, Demand.hold(toPool, queryMs, reservedSlots));
        demands.put(DB_CPU, Demand.rate(toDatabase, dbCpuCost));
        demands.put(DB_IO, Demand.rate(ioRps, ioCost));
        demands.put(DISK, Demand.flow(toCpu, logUnits, m.drainPerSecond));
        return new Plan(demands, arrivals, toCpu, edgeFailed, writeFraction, threadHoldMs);
    }

    /** A daily job runs for rows / rows_per_minute game minutes, slower when throttled or isolated. */
    private static boolean jobRunning(ThreatDef.Job job, ThreatDef.Consumption c, double rows, double since, Modifiers m) {
        if (m.jobRescheduled) {
            return false;
        }
        double durationS = rows / job.rowsPerMinute() / m.jobThrottle;
        if (m.isolatedSlots > 0) {
            durationS *= (double) c.slots() / m.isolatedSlots;
        }
        return since % DAY_SECONDS < durationS;
    }

    private Modifiers modifiers(Progress progress, long tick) {
        Modifiers m = new Modifiers();
        for (InstalledCounter installed : progress.counters()) {
            boolean active = installed.isActive(tick);
            for (Rules.Effect e : rules.counter(installed.id()).variant(installed.option()).effects()) {
                boolean applies = switch (e.kind().timing) {
                    case ACTIVE -> active;
                    case BUILD -> !active;
                    case STRUCTURAL, NARRATIVE -> false;
                };
                if (applies) {
                    m.apply(e);
                }
            }
        }
        return m;
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

    /** Requests per second a pool's free slots can complete at the given hold time. */
    private static double throughputRps(GameState s, ResourceId pool, double holdMs, int reserved) {
        return pool(s, pool).availableSlots(reserved) * 1000.0 / holdMs;
    }

    /** Previous tick's latency; before any load, the (cost-adjusted) service time. */
    private static double latencyMs(GameState s, ResourceId id, double costFactor) {
        double latency = ((UtilizationState) s.resource(id).state()).latencyMs();
        return latency > 0 ? latency : serviceTimeMs(s, id) * costFactor;
    }

    private static double serviceTimeMs(GameState s, ResourceId id) {
        return utilizationModel(s, id).serviceTimeMs();
    }

    private static OccupancyState occupancy(GameState s, ResourceId id) {
        return (OccupancyState) s.resource(id).state();
    }

    private static OccupancyModel pool(GameState s, ResourceId id) {
        return (OccupancyModel) s.resource(id).model();
    }

    private static UtilizationModel utilizationModel(GameState s, ResourceId id) {
        return (UtilizationModel) s.resource(id).model();
    }

    /** What the installed counters change this tick. Mutable, but local to one plan() call. */
    private static final class Modifiers {
        double logUnits = Double.NaN;
        double drainPerSecond;
        double rateLimitRps = Double.POSITIVE_INFINITY;
        double holdCapMs = Double.POSITIVE_INFINITY;
        double perRowMs = Double.NaN;
        boolean jobRescheduled;
        double jobThrottle = 1;
        int isolatedSlots;
        double retryMultiplier = 1;
        boolean cpuDown;
        boolean writeLocked;
        double ioLoadFactor = 1;

        void apply(Rules.Effect e) {
            switch (e.kind()) {
                case SET_UNITS_PER_REQUEST -> logUnits = e.value();
                case ADD_DRAIN -> drainPerSecond += e.value();
                case RATE_LIMIT -> rateLimitRps = Math.min(rateLimitRps, e.value());
                case CAP_HOLD_TIME -> holdCapMs = Math.min(holdCapMs, e.value());
                case SET_PER_ROW_MS -> perRowMs = e.value();
                case RESCHEDULE -> jobRescheduled = true;
                case THROTTLE_JOB -> jobThrottle *= e.value();
                case ISOLATE_SLOTS -> isolatedSlots = (int) e.value();
                case LOAD_MULTIPLIER -> retryMultiplier *= e.value();
                case DOWNTIME -> cpuDown = true;
                case WRITE_LOCK -> writeLocked = true;
                case EXTRA_LOAD -> ioLoadFactor *= 1 + e.value();
                case ADD_SERVERS, ADD_SLOTS, SCALE_CAPACITY, RETRY_FAILED, REJECT_WITH_STATUS, LESS_DEBUG_CONTEXT,
                        SHORTER_LOG_HISTORY, PROBLEM_POSTPONED, FAILED_QUERIES, MORE_WAITING_ON_POOL,
                        JOB_RUNS_LONGER -> { }
            }
        }
    }
}
