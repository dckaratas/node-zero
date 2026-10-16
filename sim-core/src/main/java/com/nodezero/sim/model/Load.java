package com.nodezero.sim.model;

/**
 * Demand placed on a resource during a tick. Each model declares the load type it accepts,
 * so a hold time can never be handed to a CPU by mistake.
 *
 * <p>Self-typed ({@code L extends Load<L>}) so updates keep the concrete type without casts.
 */
public sealed interface Load<L extends Load<L>> permits RateLoad, HoldLoad {

    double arrivalRps();

    L withArrivalRps(double arrivalRps);

    static double requireRate(double arrivalRps) {
        if (!Double.isFinite(arrivalRps) || arrivalRps < 0) {
            throw new IllegalArgumentException("arrivalRps must be finite and >= 0: " + arrivalRps);
        }
        return arrivalRps;
    }
}
