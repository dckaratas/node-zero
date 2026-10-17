package com.nodezero.sim.state;

import java.util.ArrayList;
import java.util.List;

/**
 * Game-level state next to the resources: credits, wave and budget, what is active and installed.
 * Lists keep insertion order, which is deterministic (ADR-0012).
 */
public record Progress(
        int credits,
        WaveState wave,
        List<ActiveThreat> activeThreats,
        List<InstalledCounter> counters,
        List<String> insights) {

    public static final Progress NONE = start(0);

    public Progress {
        activeThreats = List.copyOf(activeThreats);
        counters = List.copyOf(counters);
        insights = List.copyOf(insights);
    }

    public static Progress start(int credits) {
        return new Progress(credits, WaveState.PREPARATION, List.of(), List.of(), List.of());
    }

    public Progress withCredits(int newCredits) {
        return new Progress(newCredits, wave, activeThreats, counters, insights);
    }

    public Progress withWave(WaveState newWave) {
        return new Progress(credits, newWave, activeThreats, counters, insights);
    }

    public Progress withActiveThreats(List<ActiveThreat> newThreats) {
        return new Progress(credits, wave, newThreats, counters, insights);
    }

    public Progress withCounter(InstalledCounter counter) {
        List<InstalledCounter> next = new ArrayList<>(counters);
        next.add(counter);
        return new Progress(credits, wave, activeThreats, next, insights);
    }

    public Progress withInsight(String insight) {
        if (insights.contains(insight)) {
            return this;
        }
        List<String> next = new ArrayList<>(insights);
        next.add(insight);
        return new Progress(credits, wave, activeThreats, counters, next);
    }

    public boolean isThreatActive(String id) {
        for (ActiveThreat t : activeThreats) {
            if (t.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    public boolean isInstalled(String counterId) {
        for (InstalledCounter c : counters) {
            if (c.id().equals(counterId)) {
                return true;
            }
        }
        return false;
    }
}
