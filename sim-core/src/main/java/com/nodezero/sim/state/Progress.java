package com.nodezero.sim.state;

import java.util.ArrayList;
import java.util.List;

/**
 * Game-level state next to the resources: credits, wave and budget, what is active and installed.
 * Lists keep insertion order, which is deterministic (ADR-0012).
 *
 * @param postmortem the open or just-answered postmortem, or {@code null}
 * @param reviewedThreats threats whose postmortem has been answered; each is reviewed once
 */
public record Progress(
        int credits,
        WaveState wave,
        List<ActiveThreat> activeThreats,
        List<InstalledCounter> counters,
        List<String> insights,
        PostmortemState postmortem,
        List<String> reviewedThreats) {

    public static final Progress NONE = start(0);

    public Progress {
        activeThreats = List.copyOf(activeThreats);
        counters = List.copyOf(counters);
        insights = List.copyOf(insights);
        reviewedThreats = List.copyOf(reviewedThreats);
    }

    public static Progress start(int credits) {
        return new Progress(credits, WaveState.PREPARATION, List.of(), List.of(), List.of(), null, List.of());
    }

    public Progress withCredits(int newCredits) {
        return new Progress(newCredits, wave, activeThreats, counters, insights, postmortem, reviewedThreats);
    }

    public Progress withWave(WaveState newWave) {
        return new Progress(credits, newWave, activeThreats, counters, insights, postmortem, reviewedThreats);
    }

    public Progress withActiveThreats(List<ActiveThreat> newThreats) {
        return new Progress(credits, wave, newThreats, counters, insights, postmortem, reviewedThreats);
    }

    public Progress withCounter(InstalledCounter counter) {
        List<InstalledCounter> next = new ArrayList<>(counters);
        next.add(counter);
        return new Progress(credits, wave, activeThreats, next, insights, postmortem, reviewedThreats);
    }

    public Progress withInsight(String insight) {
        if (insights.contains(insight)) {
            return this;
        }
        List<String> next = new ArrayList<>(insights);
        next.add(insight);
        return new Progress(credits, wave, activeThreats, counters, next, postmortem, reviewedThreats);
    }

    /** Opens, answers or (with {@code null}) clears the postmortem. Answering marks the threat reviewed. */
    public Progress withPostmortem(PostmortemState newPostmortem) {
        List<String> reviewed = reviewedThreats;
        if (newPostmortem != null && newPostmortem.answered() && !reviewedThreats.contains(newPostmortem.threatId())) {
            reviewed = new ArrayList<>(reviewedThreats);
            reviewed.add(newPostmortem.threatId());
        }
        return new Progress(credits, wave, activeThreats, counters, insights, newPostmortem, reviewed);
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
