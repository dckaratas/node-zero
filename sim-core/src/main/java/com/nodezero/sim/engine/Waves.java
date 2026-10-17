package com.nodezero.sim.engine;

import com.nodezero.sim.content.ThreatDef;
import com.nodezero.sim.model.FillState;
import com.nodezero.sim.model.OccupancyState;
import com.nodezero.sim.model.UtilizationState;
import com.nodezero.sim.state.ActiveThreat;
import com.nodezero.sim.state.GameState;
import com.nodezero.sim.state.InstalledCounter;
import com.nodezero.sim.state.Progress;
import com.nodezero.sim.state.ResourceId;
import com.nodezero.sim.state.WaveState;
import java.util.ArrayList;
import java.util.List;

/**
 * Wave lifecycle and the error budget (ADR-0007, decisions E/F/G):
 * each wave has its own budget, ends when its traffic shape ends, pays credits by shield left minus
 * upkeep, and leaves persistent threats active for later waves.
 */
final class Waves {

    private final Rules rules;

    Waves(Rules rules) {
        this.rules = rules;
    }

    GameState start(GameState s, int number, long tick) {
        Progress p = s.progress();
        if (p.wave().phase() == WaveState.Phase.RUNNING) {
            throw new IllegalArgumentException("wave " + p.wave().number() + " is still running");
        }
        GameBalance balance = rules.balance();
        ThreatDef threat = rules.threatForWave(number);
        double duration = threat == null ? balance.quietWaveSeconds() : TrafficShape.durationS(threat.traffic());
        double expected = threat == null
                ? rules.station().baselineRps() * duration
                : TrafficShape.totalRequests(threat.traffic());

        List<ActiveThreat> active = p.activeThreats();
        if (threat != null && !p.isThreatActive(threat.id())) {
            active = new ArrayList<>(active);
            active.add(new ActiveThreat(threat.id(), tick));
        }
        return s.withProgress(p.withWave(WaveState.running(number, tick, duration, expected)).withActiveThreats(active));
    }

    /** Books this tick's requests and failures; ends the wave on an empty shield or when its time is up. */
    GameState account(GameState s, Plan plan, long tick) {
        Progress p = s.progress();
        WaveState wave = p.wave();
        if (wave.phase() != WaveState.Phase.RUNNING) {
            return s;
        }
        GameBalance balance = rules.balance();

        double failedRps = plan.edgeFailedRps()
                + occupancy(s, ResourceId.THREADS).rejectedRps()
                + occupancy(s, ResourceId.DB_POOL).rejectedRps()
                + ((UtilizationState) s.resource(ResourceId.CPU).state()).rejectedRps();
        if (((FillState) s.resource(ResourceId.DISK).state()).full()) {
            failedRps += plan.servedRps() * plan.writeFraction(); // writes fail on a full disk
        }
        double latencyMs = occupancy(s, ResourceId.THREADS).waitMs() + plan.threadHoldMs();
        if (latencyMs > balance.latencySloMs()) {
            failedRps += plan.servedRps(); // served, but too slow to count
        }
        failedRps = Math.min(failedRps, plan.arrivalsRps());

        WaveState booked = wave.record(plan.arrivalsRps() * SimConstants.DT, failedRps * SimConstants.DT, failedRps);
        if (balance.endWaveOnEmptyShield() && booked.shield(balance.slo()) <= 0) {
            return s.withProgress(p.withWave(booked.in(WaveState.Phase.FAILED)));
        }
        if ((tick - wave.startTick() + 1) * SimConstants.DT >= wave.durationS() - 1e-9) {
            return complete(s, p, booked);
        }
        return s.withProgress(p.withWave(booked));
    }

    private GameState complete(GameState s, Progress p, WaveState booked) {
        GameBalance balance = rules.balance();
        int upkeep = 0;
        for (InstalledCounter c : p.counters()) {
            upkeep += rules.counter(c.id()).def().cost().upkeepPerWave();
        }
        int reward = (int) Math.round(balance.waveReward() * booked.shield(balance.slo()));

        List<ActiveThreat> remaining = new ArrayList<>();
        for (ActiveThreat t : p.activeThreats()) {
            if (rules.content().threat(t.id()).persists()) {
                remaining.add(t);
            }
        }
        return s.withProgress(p.withWave(booked.in(WaveState.Phase.COMPLETE))
                .withCredits(p.credits() + reward - upkeep)
                .withActiveThreats(remaining));
    }

    private static OccupancyState occupancy(GameState s, ResourceId id) {
        return (OccupancyState) s.resource(id).state();
    }
}
