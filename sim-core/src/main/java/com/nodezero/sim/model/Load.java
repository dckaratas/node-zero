package com.nodezero.sim.model;

/** Demand placed on a resource during a tick. */
public record Load(double arrivalRps) {

    public Load {
        if (!Double.isFinite(arrivalRps) || arrivalRps < 0) {
            throw new IllegalArgumentException("arrivalRps must be finite and >= 0: " + arrivalRps);
        }
    }
}
