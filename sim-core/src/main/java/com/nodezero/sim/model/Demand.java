package com.nodezero.sim.model;

/**
 * Model-agnostic description of what the request pipeline asks of one resource this tick.
 * Each model turns it into its own load type via {@link ResourceModel#loadFor}.
 *
 * @param arrivalRps requests per second reaching the resource
 * @param costFactor per-request work relative to the model's baseline service time (rate models)
 * @param holdTimeMs how long each request holds a slot (pool models)
 * @param reservedSlots pool slots taken by background work (pool models)
 * @param unitsPerRequest data stored per request (fill models)
 * @param drainPerSecond data removed per second (fill models)
 */
public record Demand(
        double arrivalRps,
        double costFactor,
        double holdTimeMs,
        int reservedSlots,
        double unitsPerRequest,
        double drainPerSecond) {

    public static Demand rate(double arrivalRps, double costFactor) {
        return new Demand(arrivalRps, costFactor, 0, 0, 0, 0);
    }

    public static Demand hold(double arrivalRps, double holdTimeMs, int reservedSlots) {
        return new Demand(arrivalRps, 1, holdTimeMs, reservedSlots, 0, 0);
    }

    public static Demand flow(double arrivalRps, double unitsPerRequest, double drainPerSecond) {
        return new Demand(arrivalRps, 1, 0, 0, unitsPerRequest, drainPerSecond);
    }
}
