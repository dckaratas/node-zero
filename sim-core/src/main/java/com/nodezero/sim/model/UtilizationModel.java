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

    /** Capacity at baseline cost (cost factor 1). */
    public double capacityRps() {
        return servers * serviceRate;
    }

    /** Service time of one baseline request, in ms. */
    public double serviceTimeMs() {
        return 1000.0 / serviceRate;
    }

    /** Highest arrival rate the steady-state formulas accept at this cost (rho cap, ADR-0013). */
    public double maxSteadyRps(double costFactor) {
        return ErlangC.RHO_CAP * capacityRps() / costFactor;
    }

    /** Erlang-C mean response time in ms for {@code lambda} at this cost, without any backlog. */
    public double steadyLatencyMs(double lambda, double costFactor) {
        double rate = serviceRate / costFactor;
        return 1000.0 / rate + ErlangC.cappedMeanWaitMs(servers, rate, lambda);
    }

    @Override
    public RateLoad loadFor(Demand demand) {
        return new RateLoad(demand.arrivalRps(), demand.costFactor());
    }

    @Override
    public UtilizationState step(UtilizationState previous, RateLoad load, double dt) {
        double lambda = load.arrivalRps();
        double rate = serviceRate / load.costFactor();
        double capacity = servers * rate;
        Backlog backlog = Backlog.next(previous.queue(), lambda, capacity, queueLimit, dt);

        double serviceMs = 1000.0 / rate;
        double waitMs = ErlangC.cappedMeanWaitMs(servers, rate, lambda);
        double drainMs = 1000.0 * backlog.size() / capacity;

        return new UtilizationState(
                backlog.size(), lambda / capacity, serviceMs + waitMs + drainMs, backlog.rejectedRps());
    }
}
