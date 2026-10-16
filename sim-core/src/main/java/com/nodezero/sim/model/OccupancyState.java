package com.nodezero.sim.model;

/**
 * Output of {@link OccupancyModel} for one tick, shaped like HikariCP's pool metrics.
 *
 * @param active slots in use (hikaricp_connections_active)
 * @param pending requests waiting for a slot (hikaricp_connections_pending), at most the queue limit
 * @param waitMs mean time waiting for a slot, excluding the hold time itself
 * @param rejectedRps arrivals dropped because the wait queue was full
 * @param backlog overflow carried to the next tick; drives {@code pending} under saturation
 */
public record OccupancyState(double active, double pending, double waitMs, double rejectedRps, double backlog)
        implements ResourceState {

    public static OccupancyState initial() {
        return new OccupancyState(0, 0, 0, 0, 0);
    }
}
