package com.nodezero.sim.model;

/**
 * Load for slot pools where each request holds a slot for {@code holdTimeMs} (threads, DB connections).
 * Hold time belongs to the load, not the pool: a slow query makes every request hold longer.
 */
public record HoldLoad(double arrivalRps, double holdTimeMs) implements Load<HoldLoad> {

    public HoldLoad {
        Load.requireRate(arrivalRps);
        if (!Double.isFinite(holdTimeMs) || holdTimeMs <= 0) {
            throw new IllegalArgumentException("holdTimeMs must be finite and > 0: " + holdTimeMs);
        }
    }

    @Override
    public HoldLoad withArrivalRps(double newArrivalRps) {
        return new HoldLoad(newArrivalRps, holdTimeMs);
    }

    public HoldLoad withHoldTimeMs(double newHoldTimeMs) {
        return new HoldLoad(arrivalRps, newHoldTimeMs);
    }
}
