package com.nodezero.sim.model;

/**
 * Load for accumulating resources (disk space, leaked memory). Inflow scales with traffic:
 * {@code inflow = arrivalRps * unitsPerRequest}, e.g. 200 rps x 0.002 MB of DEBUG logs.
 *
 * @param arrivalRps requests per second producing data
 * @param unitsPerRequest data written per request (e.g. MB); lowered by fixing the log level
 * @param drainPerSecond data removed per second regardless of traffic (e.g. logrotate)
 */
public record FlowLoad(double arrivalRps, double unitsPerRequest, double drainPerSecond)
        implements Load<FlowLoad> {

    public FlowLoad {
        Load.requireRate(arrivalRps);
        if (!Double.isFinite(unitsPerRequest) || unitsPerRequest < 0) {
            throw new IllegalArgumentException("unitsPerRequest must be finite and >= 0: " + unitsPerRequest);
        }
        if (!Double.isFinite(drainPerSecond) || drainPerSecond < 0) {
            throw new IllegalArgumentException("drainPerSecond must be finite and >= 0: " + drainPerSecond);
        }
    }

    public double inflowPerSecond() {
        return arrivalRps * unitsPerRequest;
    }

    @Override
    public FlowLoad withArrivalRps(double newArrivalRps) {
        return new FlowLoad(newArrivalRps, unitsPerRequest, drainPerSecond);
    }
}
