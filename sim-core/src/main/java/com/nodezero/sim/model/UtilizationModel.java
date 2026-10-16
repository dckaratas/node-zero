package com.nodezero.sim.model;

/**
 * Rate-limited resource with {@code servers} parallel workers sharing one queue (M/M/c).
 *
 * <p>Latency is Erlang-C below saturation. At or above saturation the steady-state term is
 * frozen at {@link ErlangC#RHO_CAP} and the backlog drain time grows instead (ADR-0013).
 *
 * @param servers number of parallel workers (c), e.g. CPU cores
 * @param serviceRate requests per second one worker completes (mu)
 * @param queueLimit backlog size above which arrivals are rejected
 */
public record UtilizationModel(int servers, double serviceRate, int queueLimit)
        implements ResourceModel<UtilizationState, RateLoad> {

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
    public UtilizationState step(UtilizationState previous, RateLoad load, double dt) {
        double lambda = load.arrivalRps();
        double capacity = capacityRps();
        Backlog backlog = Backlog.next(previous.queue(), lambda, capacity, queueLimit, dt);

        double serviceMs = 1000.0 / serviceRate;
        double waitMs = ErlangC.cappedMeanWaitMs(servers, serviceRate, lambda);
        double drainMs = 1000.0 * backlog.size() / capacity;

        return new UtilizationState(
                backlog.size(), lambda / capacity, serviceMs + waitMs + drainMs, backlog.rejectedRps());
    }
}
