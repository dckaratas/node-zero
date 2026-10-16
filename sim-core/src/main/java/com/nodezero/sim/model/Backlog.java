package com.nodezero.sim.model;

/**
 * Overflow queue carried between ticks when arrivals exceed capacity (fluid approximation).
 *
 * @param size requests waiting after this tick, within {@code [0, limit]}
 * @param rejectedRps arrivals dropped this tick because the queue was full
 */
record Backlog(double size, double rejectedRps) {

    static Backlog next(double previous, double lambda, double capacity, int limit, double dt) {
        if (!(dt > 0)) {
            throw new IllegalArgumentException("dt must be > 0: " + dt);
        }
        double unbounded = previous + (lambda - capacity) * dt;
        return new Backlog(Math.clamp(unbounded, 0, limit), Math.max(0, unbounded - limit) / dt);
    }
}
