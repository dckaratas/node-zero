package com.nodezero.sim.model;

/** Erlang-C (M/M/c) formulas shared by rate-based and slot-based models (ADR-0013). */
final class ErlangC {

    /** Above this utilization the formulas diverge; callers cap lambda and model the backlog instead. */
    static final double RHO_CAP = 0.99;

    private ErlangC() {}

    /** Mean time spent waiting for a free server, in ms. Requires {@code lambda < servers * serviceRate}. */
    static double meanWaitMs(int servers, double serviceRate, double lambda) {
        double offeredLoad = lambda / serviceRate; // a, in Erlangs
        // Erlang-B by recursion: avoids a^c / c! overflow for large c.
        double erlangB = 1.0;
        for (int k = 1; k <= servers; k++) {
            erlangB = offeredLoad * erlangB / (k + offeredLoad * erlangB);
        }
        double capacity = servers * serviceRate;
        double rho = lambda / capacity;
        double waitProbability = erlangB / (1 - rho * (1 - erlangB));
        return 1000.0 * waitProbability / (capacity - lambda);
    }

    /** {@link #meanWaitMs} with lambda capped at {@link #RHO_CAP} of capacity. */
    static double cappedMeanWaitMs(int servers, double serviceRate, double lambda) {
        return meanWaitMs(servers, serviceRate, Math.min(lambda, RHO_CAP * servers * serviceRate));
    }
}
