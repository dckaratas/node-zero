package com.nodezero.sim.engine;

import com.nodezero.sim.content.ModelKind;

/**
 * Every effect type content may use (decision C: YAML picks the type, Java defines behaviour).
 * Unknown types are rejected when rules are built.
 */
enum EffectKind {
    // Change parameters while the counter is active.
    SET_UNITS_PER_REQUEST(Timing.ACTIVE, ModelKind.FILL),
    ADD_DRAIN(Timing.ACTIVE, ModelKind.FILL),
    RATE_LIMIT(Timing.ACTIVE, ModelKind.UTILIZATION),
    CAP_HOLD_TIME(Timing.ACTIVE, ModelKind.OCCUPANCY),
    SET_PER_ROW_MS(Timing.ACTIVE, ModelKind.UTILIZATION),
    RESCHEDULE(Timing.ACTIVE, null),
    THROTTLE_JOB(Timing.ACTIVE, ModelKind.UTILIZATION),
    ISOLATE_SLOTS(Timing.ACTIVE, ModelKind.OCCUPANCY),
    LOAD_MULTIPLIER(Timing.ACTIVE, ModelKind.OCCUPANCY),
    // Change the station's hardware once, when the counter becomes active.
    ADD_SERVERS(Timing.STRUCTURAL, ModelKind.UTILIZATION),
    ADD_SLOTS(Timing.STRUCTURAL, ModelKind.OCCUPANCY),
    SCALE_CAPACITY(Timing.STRUCTURAL, ModelKind.FILL),
    // Hurt only while the counter is being built (between apply and active).
    DOWNTIME(Timing.BUILD, ModelKind.UTILIZATION),
    WRITE_LOCK(Timing.BUILD, ModelKind.OCCUPANCY),
    EXTRA_LOAD(Timing.BUILD, ModelKind.UTILIZATION),
    // Shown to the player and explained in the codex; no simulated effect of their own.
    RETRY_FAILED(Timing.NARRATIVE, null),
    REJECT_WITH_STATUS(Timing.NARRATIVE, null),
    LESS_DEBUG_CONTEXT(Timing.NARRATIVE, null),
    SHORTER_LOG_HISTORY(Timing.NARRATIVE, null),
    PROBLEM_POSTPONED(Timing.NARRATIVE, null),
    FAILED_QUERIES(Timing.NARRATIVE, null),
    MORE_WAITING_ON_POOL(Timing.NARRATIVE, null),
    JOB_RUNS_LONGER(Timing.NARRATIVE, null);

    enum Timing {
        ACTIVE,
        STRUCTURAL,
        BUILD,
        NARRATIVE
    }

    final Timing timing;
    /** Model kind the target resource must have, or {@code null} if the effect takes no target. */
    final ModelKind targetKind;

    EffectKind(Timing timing, ModelKind targetKind) {
        this.timing = timing;
        this.targetKind = targetKind;
    }

    boolean needsValue() {
        return timing != Timing.NARRATIVE && this != RESCHEDULE;
    }
}
