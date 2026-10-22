package com.nodezero.sim.engine;

import com.nodezero.sim.content.ModelKind;

/**
 * Every effect type content may use (decision C: YAML picks the type, Java defines behaviour).
 * Unknown types are rejected when rules are built.
 */
enum EffectKind {
    // Change parameters while the counter is active.
    SET_UNITS_PER_REQUEST(Timing.ACTIVE, ModelKind.FILL, true),
    ADD_DRAIN(Timing.ACTIVE, ModelKind.FILL, true),
    RATE_LIMIT(Timing.ACTIVE, ModelKind.UTILIZATION, true),
    CAP_HOLD_TIME(Timing.ACTIVE, ModelKind.OCCUPANCY, true),
    SET_PER_ROW_MS(Timing.ACTIVE, ModelKind.UTILIZATION, true),
    RESCHEDULE(Timing.ACTIVE, null, false),
    THROTTLE_JOB(Timing.ACTIVE, ModelKind.UTILIZATION, true),
    ISOLATE_SLOTS(Timing.ACTIVE, ModelKind.OCCUPANCY, true),
    LOAD_MULTIPLIER(Timing.ACTIVE, ModelKind.OCCUPANCY, true),
    // Lift the fog of war while active (slice 3c).
    ENABLE_PROMETHEUS(Timing.ACTIVE, null, false),
    ENABLE_POSTGRES_EXPORTER(Timing.ACTIVE, null, false),
    ENABLE_GRAFANA(Timing.ACTIVE, null, false),
    // Change the station's hardware once, when the counter becomes active.
    ADD_SERVERS(Timing.STRUCTURAL, ModelKind.UTILIZATION, true),
    ADD_SLOTS(Timing.STRUCTURAL, ModelKind.OCCUPANCY, true),
    SCALE_CAPACITY(Timing.STRUCTURAL, ModelKind.FILL, true),
    // Hurt only while the counter is being built (between apply and active), or in an action's window.
    DOWNTIME(Timing.BUILD, ModelKind.UTILIZATION, true),
    WRITE_LOCK(Timing.BUILD, ModelKind.OCCUPANCY, true),
    EXTRA_LOAD(Timing.BUILD, ModelKind.UTILIZATION, true),
    COLD_START(Timing.BUILD, ModelKind.UTILIZATION, true),
    PAUSE_JOB(Timing.BUILD, null, false),
    // Incident actions (slice 7b): happen once, when the action runs.
    RESET_QUEUES(Timing.INSTANT, null, false),
    FREE_ORPHANED_DISK(Timing.INSTANT, null, false),
    FREE_LOGS(Timing.INSTANT, null, true),
    ORPHAN_LOGS(Timing.INSTANT, null, true),
    FREE_ARCHIVES(Timing.INSTANT, ModelKind.FILL, true),
    CANCEL_QUERIES(Timing.INSTANT, null, false),
    ROLLBACK_DEPLOY(Timing.INSTANT, null, false),
    // Shown to the player and explained in the codex; no simulated effect of their own.
    RETRY_FAILED(Timing.NARRATIVE, null, false),
    REJECT_WITH_STATUS(Timing.NARRATIVE, null, false),
    LESS_DEBUG_CONTEXT(Timing.NARRATIVE, null, false),
    SHORTER_LOG_HISTORY(Timing.NARRATIVE, null, false),
    PROBLEM_POSTPONED(Timing.NARRATIVE, null, false),
    FAILED_QUERIES(Timing.NARRATIVE, null, false),
    MORE_WAITING_ON_POOL(Timing.NARRATIVE, null, false),
    JOB_RUNS_LONGER(Timing.NARRATIVE, null, false);

    enum Timing {
        ACTIVE,
        STRUCTURAL,
        BUILD,
        INSTANT,
        NARRATIVE
    }

    final Timing timing;
    /** Model kind the target resource must have, or {@code null} if the effect takes no target. */
    final ModelKind targetKind;
    final boolean needsValue;

    EffectKind(Timing timing, ModelKind targetKind, boolean needsValue) {
        this.timing = timing;
        this.targetKind = targetKind;
        this.needsValue = needsValue;
    }
}
