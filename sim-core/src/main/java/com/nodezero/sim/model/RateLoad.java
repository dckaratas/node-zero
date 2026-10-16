package com.nodezero.sim.model;

/** Load for rate-limited resources whose service time is a property of the resource (CPU, disk I/O). */
public record RateLoad(double arrivalRps) implements Load<RateLoad> {

    public RateLoad {
        Load.requireRate(arrivalRps);
    }

    @Override
    public RateLoad withArrivalRps(double newArrivalRps) {
        return new RateLoad(newArrivalRps);
    }
}
