package com.nodezero.sim.model;

/**
 * Load for slot pools where each request holds a slot for {@code holdTimeMs} (threads, DB connections).
 * Hold time belongs to the load, not the pool: a slow query makes every request hold longer.
 *
 * @param reservedSlots slots taken by background work (e.g. a batch job) and unavailable to requests
 */
public record HoldLoad(double arrivalRps, double holdTimeMs, int reservedSlots) implements Load<HoldLoad> {

    public HoldLoad {
        Load.requireRate(arrivalRps);
        if (!Double.isFinite(holdTimeMs) || holdTimeMs <= 0) {
            throw new IllegalArgumentException("holdTimeMs must be finite and > 0: " + holdTimeMs);
        }
        if (reservedSlots < 0) {
            throw new IllegalArgumentException("reservedSlots must be >= 0: " + reservedSlots);
        }
    }

    public HoldLoad(double arrivalRps, double holdTimeMs) {
        this(arrivalRps, holdTimeMs, 0);
    }

    @Override
    public HoldLoad withArrivalRps(double newArrivalRps) {
        return new HoldLoad(newArrivalRps, holdTimeMs, reservedSlots);
    }

    public HoldLoad withHoldTimeMs(double newHoldTimeMs) {
        return new HoldLoad(arrivalRps, newHoldTimeMs, reservedSlots);
    }
}
