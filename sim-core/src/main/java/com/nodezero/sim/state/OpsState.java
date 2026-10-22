package com.nodezero.sim.state;

import java.util.ArrayList;
import java.util.List;

/**
 * The on-call crew and what its hands-on work left behind (slice 7b, ADR-0018 AF/AG).
 *
 * @param crew crew points left in this shift; actions and counters with toil spend them, a finished wave refills them
 * @param runs latest run of each action that has been used, in first-use order
 * @param orphanedMb disk space held by log files deleted while still open; a restart frees it
 * @param archivesCleared old log archives are gone; deleting them again frees nothing
 */
public record OpsState(int crew, List<ActionRun> runs, double orphanedMb, boolean archivesCleared) {

    public OpsState {
        runs = List.copyOf(runs);
    }

    public static OpsState start(int crew) {
        return new OpsState(crew, List.of(), 0, false);
    }

    public OpsState withCrew(int newCrew) {
        return new OpsState(newCrew, runs, orphanedMb, archivesCleared);
    }

    public OpsState withOrphanedMb(double mb) {
        return new OpsState(crew, runs, mb, archivesCleared);
    }

    public OpsState withArchivesCleared() {
        return new OpsState(crew, runs, orphanedMb, true);
    }

    /** Records a run, replacing the previous run of the same action. */
    public OpsState withRun(ActionRun run) {
        List<ActionRun> next = new ArrayList<>(runs.size() + 1);
        boolean replaced = false;
        for (ActionRun r : runs) {
            if (r.id().equals(run.id())) {
                next.add(run);
                replaced = true;
            } else {
                next.add(r);
            }
        }
        if (!replaced) {
            next.add(run);
        }
        return new OpsState(crew, next, orphanedMb, archivesCleared);
    }

    /** @return the latest run of an action, or {@code null} if it never ran */
    public ActionRun run(String actionId) {
        for (ActionRun r : runs) {
            if (r.id().equals(actionId)) {
                return r;
            }
        }
        return null;
    }
}
