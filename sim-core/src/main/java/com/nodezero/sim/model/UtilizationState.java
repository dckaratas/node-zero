package com.nodezero.sim.model;

/**
 * Output of {@link UtilizationModel} for one tick.
 *
 * @param queue requests waiting because arrivals exceeded capacity (backlog)
 * @param rho utilization lambda / (c * mu); may exceed 1 under overload
 * @param latencyMs mean response time including backlog drain time
 * @param rejectedRps arrivals dropped because the queue was full
 */
public record UtilizationState(double queue, double rho, double latencyMs, double rejectedRps)
        implements ResourceState {

    public static UtilizationState initial() {
        return new UtilizationState(0, 0, 0, 0);
    }
}
