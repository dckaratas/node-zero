package com.nodezero.sim.state;

/**
 * The current wave and its error budget (ADR-0007). Wave 0 is the preparation phase.
 *
 * @param number current wave; matches {@code trigger.wave} of the threat it brings
 * @param startTick tick at which the wave started; time into the wave is {@code (tick - startTick) * DT}
 * @param durationS length of the wave's traffic shape
 * @param windowRequests requests of the SLO window the budget is taken from; the budget is (1 - SLO) of this
 *     (decision AK: a station day at baseline traffic, so a wave cannot burn more than it is given)
 * @param requests requests that arrived so far
 * @param failedRequests requests that failed or breached the latency SLO so far
 * @param lastFailedRps failure rate of the previous tick (retries react to it)
 * @param symptomSinceTick first tick of the current uninterrupted run of failures, or -1 (user complaints)
 */
public record WaveState(
        int number,
        long startTick,
        Phase phase,
        double durationS,
        double windowRequests,
        double requests,
        double failedRequests,
        double lastFailedRps,
        long symptomSinceTick) {

    public enum Phase {
        PREPARATION,
        RUNNING,
        COMPLETE,
        FAILED
    }

    public static final WaveState PREPARATION = new WaveState(0, 0, Phase.PREPARATION, 0, 0, 0, 0, 0, -1);

    public WaveState {
        if (number < 0) {
            throw new IllegalArgumentException("wave number must be >= 0: " + number);
        }
        if (startTick < 0) {
            throw new IllegalArgumentException("startTick must be >= 0: " + startTick);
        }
    }

    public static WaveState running(int number, long startTick, double durationS, double windowRequests) {
        return new WaveState(number, startTick, Phase.RUNNING, durationS, windowRequests, 0, 0, 0, -1);
    }

    /** Shield energy in [0, 1]: the share of the error budget still unspent. */
    public double shield(double slo) {
        double allowance = (1 - slo) * windowRequests;
        return allowance <= 0 ? 1 : Math.clamp(1 - failedRequests / allowance, 0, 1);
    }

    public WaveState record(double arrived, double failed, double failedRps, long tick) {
        long since = failedRps <= 0 ? -1 : (symptomSinceTick < 0 ? tick : symptomSinceTick);
        return new WaveState(number, startTick, phase, durationS, windowRequests,
                requests + arrived, failedRequests + failed, failedRps, since);
    }

    /** Requests dropped at once outside the request pipeline, e.g. by a restart (slice 7b). */
    public WaveState withExtraFailures(double failed) {
        return new WaveState(number, startTick, phase, durationS, windowRequests, requests, failedRequests + failed,
                lastFailedRps, symptomSinceTick);
    }

    public WaveState in(Phase newPhase) {
        return new WaveState(number, startTick, newPhase, durationS, windowRequests,
                requests, failedRequests, 0, -1);
    }
}
