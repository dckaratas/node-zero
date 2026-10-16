package com.nodezero.sim.model;

/**
 * Rate-limited resource with {@code servers} parallel workers sharing one queue (M/M/c).
 *
 * <p>Latency is Erlang-C below saturation. At or above saturation the steady-state term is
 * frozen at {@link #RHO_CAP} and the backlog drain time grows instead (ADR-0013).
 *
 * @param servers number of parallel workers (c), e.g. CPU cores
 * @param serviceRate requests per second one worker completes (mu)
 * @param queueLimit backlog size above which arrivals are rejected
 */
public record UtilizationModel(int servers, double serviceRate, int queueLimit)
        implements ResourceModel<UtilizationState> {

    /** Above this utilization the Erlang-C formula diverges; the backlog term takes over. */
    static final double RHO_CAP = 0.99;

    public UtilizationModel {
        if (servers <= 0) {
            throw new IllegalArgumentException("servers must be > 0: " + servers);
        }
        if (!Double.isFinite(serviceRate) || serviceRate <= 0) {
            throw new IllegalArgumentException("serviceRate must be finite and > 0: " + serviceRate);
        }
        if (queueLimit < 0) {
            throw new IllegalArgumentException("queueLimit must be >= 0: " + queueLimit);
        }
    }

    public double capacityRps() {
        return servers * serviceRate;
    }

    @Override
    public UtilizationState step(UtilizationState previous, Load load, double dt) {
        if (!(dt > 0)) {
            throw new IllegalArgumentException("dt must be > 0: " + dt);
        }
        double lambda = load.arrivalRps();
        double capacity = capacityRps();

        double backlog = previous.queue() + (lambda - capacity) * dt;
        double queue = Math.clamp(backlog, 0, queueLimit);
        double rejectedRps = Math.max(0, backlog - queueLimit) / dt;

        double steadyMs = erlangCLatencyMs(Math.min(lambda, RHO_CAP * capacity));
        double drainMs = 1000.0 * queue / capacity;

        return new UtilizationState(queue, lambda / capacity, steadyMs + drainMs, rejectedRps);
    }

    /** Mean M/M/c response time in ms. Requires {@code lambda < capacity}. */
    private double erlangCLatencyMs(double lambda) {
        double offeredLoad = lambda / serviceRate; // a, in Erlangs
        // Erlang-B by recursion: avoids a^c / c! overflow for large c.
        double erlangB = 1.0;
        for (int k = 1; k <= servers; k++) {
            erlangB = offeredLoad * erlangB / (k + offeredLoad * erlangB);
        }
        double rho = lambda / capacityRps();
        double waitProbability = erlangB / (1 - rho * (1 - erlangB)); // Erlang-C
        double waitSeconds = waitProbability / (capacityRps() - lambda);
        return 1000.0 * (1 / serviceRate + waitSeconds);
    }
}
