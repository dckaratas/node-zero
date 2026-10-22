package com.nodezero.sim.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Game-level state next to the resources: credits, wave and budget, what is active and installed.
 * Lists keep insertion order, which is deterministic (ADR-0012).
 *
 * @param postmortem the open or just-answered postmortem, or {@code null}
 * @param reviewedThreats threats whose postmortem has been answered; each is reviewed once
 * @param codex codex entries the player has opened, in the order they were earned
 * @param results how each played wave ended, by wave number (chapter summary)
 * @param ops the on-call crew and the incident actions it ran (slice 7b)
 */
public record Progress(
        int credits,
        WaveState wave,
        List<ActiveThreat> activeThreats,
        List<InstalledCounter> counters,
        List<String> insights,
        PostmortemState postmortem,
        List<String> reviewedThreats,
        List<String> codex,
        List<WaveResult> results,
        OpsState ops) {

    public static final Progress NONE = start(0);

    public Progress {
        activeThreats = List.copyOf(activeThreats);
        counters = List.copyOf(counters);
        insights = List.copyOf(insights);
        reviewedThreats = List.copyOf(reviewedThreats);
        codex = List.copyOf(codex);
        results = List.copyOf(results);
        Objects.requireNonNull(ops, "ops");
    }

    public static Progress start(int credits) {
        return new Progress(credits, WaveState.PREPARATION, List.of(), List.of(), List.of(), null, List.of(), List.of(),
                List.of(), OpsState.start(0));
    }

    public Progress withCredits(int newCredits) {
        return new Progress(newCredits, wave, activeThreats, counters, insights, postmortem,
                reviewedThreats, codex, results, ops);
    }

    public Progress withWave(WaveState newWave) {
        return new Progress(credits, newWave, activeThreats, counters, insights, postmortem,
                reviewedThreats, codex, results, ops);
    }

    public Progress withActiveThreats(List<ActiveThreat> newThreats) {
        return new Progress(credits, wave, newThreats, counters, insights, postmortem,
                reviewedThreats, codex, results, ops);
    }

    public Progress withCounter(InstalledCounter counter) {
        List<InstalledCounter> next = new ArrayList<>(counters);
        next.add(counter);
        return new Progress(credits, wave, activeThreats, next, insights, postmortem,
                reviewedThreats, codex, results, ops);
    }

    /** Replaces the installed counters, e.g. after one was removed or reconfigured. */
    public Progress withCounters(List<InstalledCounter> newCounters) {
        return new Progress(credits, wave, activeThreats, newCounters, insights, postmortem, reviewedThreats, codex,
                results, ops);
    }

    public Progress withInsight(String insight) {
        if (insights.contains(insight)) {
            return this;
        }
        List<String> next = new ArrayList<>(insights);
        next.add(insight);
        return new Progress(credits, wave, activeThreats, counters, next, postmortem,
                reviewedThreats, codex, results, ops);
    }

    /** Opens, answers or (with {@code null}) clears the postmortem. Answering marks the threat reviewed. */
    public Progress withPostmortem(PostmortemState newPostmortem) {
        List<String> reviewed = reviewedThreats;
        if (newPostmortem != null && newPostmortem.answered() && !reviewedThreats.contains(newPostmortem.threatId())) {
            reviewed = new ArrayList<>(reviewedThreats);
            reviewed.add(newPostmortem.threatId());
        }
        return new Progress(credits, wave, activeThreats, counters, insights, newPostmortem,
                reviewed, codex, results, ops);
    }

    /** Opens codex entries; ones already open keep their place. */
    public Progress withCodex(List<String> entries) {
        List<String> next = null;
        for (String id : entries) {
            if (!codex.contains(id) && (next == null || !next.contains(id))) {
                if (next == null) {
                    next = new ArrayList<>(codex);
                }
                next.add(id);
            }
        }
        return next == null ? this : new Progress(credits, wave, activeThreats, counters, insights, postmortem,
                reviewedThreats, next, results, ops);
    }

    /**
     * Records how a wave ended, replacing an earlier result of the same wave. A replayed threat wave asks
     * no new postmortem, so it keeps the earlier verdict.
     */
    public Progress withResult(WaveResult result) {
        List<WaveResult> next = new ArrayList<>(results.size() + 1);
        boolean placed = false;
        for (WaveResult r : results) {
            if (r.wave() == result.wave()) {
                next.add(result.postmortemRight() == null && r.postmortemRight() != null
                        ? result.withPostmortemRight(r.postmortemRight()) : result);
                placed = true;
            } else {
                if (!placed && r.wave() > result.wave()) {
                    next.add(result);
                    placed = true;
                }
                next.add(r);
            }
        }
        if (!placed) {
            next.add(result);
        }
        return new Progress(credits, wave, activeThreats, counters, insights, postmortem,
                reviewedThreats, codex, next, ops);
    }

    /** Stores the postmortem verdict on the result of the wave it reviewed. */
    public Progress withPostmortemVerdict(int reviewedWave, boolean right) {
        List<WaveResult> next = new ArrayList<>(results.size());
        for (WaveResult r : results) {
            next.add(r.wave() == reviewedWave ? r.withPostmortemRight(right) : r);
        }
        return new Progress(credits, wave, activeThreats, counters, insights, postmortem,
                reviewedThreats, codex, next, ops);
    }

    public Progress withOps(OpsState newOps) {
        return new Progress(credits, wave, activeThreats, counters, insights, postmortem,
                reviewedThreats, codex, results, newOps);
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
