package com.nodezero.sim.model;

/**
 * Accumulating resource: {@code level += (inflow - drain) * dt}, bounded by {@code [0, capacity]}.
 * Unlike the queueing models nothing is served; what goes in stays until drained (disk space, leaks).
 *
 * @param capacity maximum amount that can be stored (e.g. disk size in MB)
 */
public record FillModel(double capacity) implements ResourceModel<FillState, FlowLoad> {

    public FillModel {
        if (!Double.isFinite(capacity) || capacity <= 0) {
            throw new IllegalArgumentException("capacity must be finite and > 0: " + capacity);
        }
    }

    @Override
    public FillState step(FillState previous, FlowLoad load, double dt) {
        if (!(dt > 0)) {
            throw new IllegalArgumentException("dt must be > 0: " + dt);
        }
        double netPerSecond = load.inflowPerSecond() - load.drainPerSecond();
        double unbounded = previous.level() + netPerSecond * dt;
        double level = Math.clamp(unbounded, 0, capacity);
        double droppedPerSecond = Math.max(0, unbounded - capacity) / dt;
        double secondsToFull = netPerSecond > 0 ? (capacity - level) / netPerSecond : Double.POSITIVE_INFINITY;

        return new FillState(level, level / capacity, level >= capacity, droppedPerSecond, secondsToFull);
    }
}
