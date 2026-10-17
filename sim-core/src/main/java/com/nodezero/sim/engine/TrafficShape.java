package com.nodezero.sim.engine;

import com.nodezero.sim.content.ThreatDef;

/** Arrival rate of a threat's traffic over time: baseline x the multiplier of the current phase. */
final class TrafficShape {

    private TrafficShape() {}

    static double rateAt(ThreatDef.Traffic traffic, double secondsIntoWave) {
        double phaseStart = 0;
        ThreatDef.Phase last = null;
        for (ThreatDef.Phase phase : traffic.shape()) {
            if (secondsIntoWave < phaseStart + phase.durationS()) {
                double progress = (secondsIntoWave - phaseStart) / phase.durationS();
                return traffic.baselineRps() * phase.multiplier().at(progress);
            }
            phaseStart += phase.durationS();
            last = phase;
        }
        // After the shape ends traffic settles at the last phase's final multiplier.
        return traffic.baselineRps() * (last == null ? 1.0 : last.multiplier().to());
    }

    static double durationS(ThreatDef.Traffic traffic) {
        double total = 0;
        for (ThreatDef.Phase phase : traffic.shape()) {
            total += phase.durationS();
        }
        return total;
    }

    /** Requests the whole shape brings: the area under the (piecewise linear) rate curve. */
    static double totalRequests(ThreatDef.Traffic traffic) {
        double total = 0;
        for (ThreatDef.Phase phase : traffic.shape()) {
            double meanMultiplier = (phase.multiplier().from() + phase.multiplier().to()) / 2;
            total += traffic.baselineRps() * meanMultiplier * phase.durationS();
        }
        return total;
    }
}
