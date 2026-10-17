package com.nodezero.sim.model;

/**
 * Output of {@link FillModel} for one tick.
 *
 * @param level amount stored, within {@code [0, capacity]}
 * @param fillRatio level / capacity (what a disk usage panel shows)
 * @param full true while level has reached capacity; clears again once space is freed
 * @param droppedPerSecond inflow that could not be stored this tick (failed writes)
 * @param secondsToFull time until full at the current net inflow, like Prometheus
 *     {@code predict_linear}; {@link Double#POSITIVE_INFINITY} when the level is not rising
 */
public record FillState(double level, double fillRatio, boolean full, double droppedPerSecond, double secondsToFull)
        implements ResourceState {

    public static FillState empty() {
        return new FillState(0, 0, false, 0, Double.POSITIVE_INFINITY);
    }
}
