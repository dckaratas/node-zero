package com.nodezero.sim.model;

/**
 * Load for rate-limited resources (CPU, disk I/O).
 *
 * @param arrivalRps requests per second
 * @param costFactor work per request relative to the model's baseline service time; 2.0 means each
 *     request takes twice as long (e.g. a query scanning more rows)
 */
public record RateLoad(double arrivalRps, double costFactor) implements Load<RateLoad> {

    public RateLoad {
        Load.requireRate(arrivalRps);
        if (!Double.isFinite(costFactor) || costFactor <= 0) {
            throw new IllegalArgumentException("costFactor must be finite and > 0: " + costFactor);
        }
    }

    public RateLoad(double arrivalRps) {
        this(arrivalRps, 1.0);
    }

    @Override
    public RateLoad withArrivalRps(double newArrivalRps) {
        return new RateLoad(newArrivalRps, costFactor);
    }
}
