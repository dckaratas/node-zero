package com.nodezero.sim.engine;

import com.nodezero.sim.content.ThreatDef;
import com.nodezero.sim.model.FillState;
import com.nodezero.sim.model.OccupancyState;
import com.nodezero.sim.model.UtilizationState;
import com.nodezero.sim.state.ActiveThreat;
import com.nodezero.sim.state.GameState;
import com.nodezero.sim.state.InstalledCounter;
import com.nodezero.sim.state.PostmortemState;
import com.nodezero.sim.state.Progress;
import com.nodezero.sim.state.ResourceId;
import com.nodezero.sim.state.WaveResult;
import com.nodezero.sim.state.WaveState;
import java.util.ArrayList;
import java.util.List;

/**
 * Wave lifecycle and the error budget (ADR-0007, decisions E/F/G):
 * each wave has its own budget, ends when its traffic shape ends, pays credits by shield left minus
 * upkeep, and leaves persistent threats active for later waves.
 */
final class Waves {

    /** Slow-request shares below one in a million are treated as none (decision AL). */
    static final double NEGLIGIBLE_SHARE = 1e-6;

    private final Rules rules;

    Waves(Rules rules) {
        this.rules = rules;
    }

    GameState start(GameState s, int number, long tick) {
        Progress p = s.progress();
        if (p.wave().phase() == WaveState.Phase.RUNNING) {
            throw new IllegalArgumentException("wave " + p.wave().number() + " is still running");
        }
        if (number > rules.lastWave()) {
            throw new IllegalArgumentException(
                    "chapter " + rules.chapter() + " ends at wave " + rules.lastWave() + ": no wave " + number);
        }
        if (p.postmortem() != null && !p.postmortem().answered()) {
            throw new IllegalArgumentException(
                    "answer the postmortem of wave " + p.postmortem().wave() + " before starting the next wave");
        }
        p = p.withPostmortem(null);
        GameBalance balance = rules.balance();
        ThreatDef threat = rules.threatForWave(number);
        double duration = threat == null ? balance.quietWaveSeconds() : TrafficShape.durationS(threat.traffic());
        // Every wave gets the budget of one station day (decision AK), whatever traffic it brings.
        double windowRequests = rules.station().baselineRps() * balance.budgetWindowS();

        List<ActiveThreat> active = p.activeThreats();
        if (threat != null && !p.isThreatActive(threat.id())) {
            active = new ArrayList<>(active);
            active.add(new ActiveThreat(threat.id(), tick));
        }
        return s.withProgress(p.withWave(WaveState.running(number, tick, duration, windowRequests))
                .withActiveThreats(active));
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
        failedRps += plan.servedRps() * slowShare(latencyMs, balance.latencySloMs()); // served, but too slow
        failedRps = Math.min(failedRps, plan.arrivalsRps());

        WaveState booked = wave.record(
                plan.arrivalsRps() * SimConstants.DT, failedRps * SimConstants.DT, failedRps, tick);
        if (balance.endWaveOnEmptyShield() && booked.shield(balance.slo()) <= 0) {
            WaveResult failed = new WaveResult(wave.number(), false, booked.shield(balance.slo()), 0, null);
            return s.withProgress(review(p.withWave(booked.in(WaveState.Phase.FAILED)).withResult(failed)));
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
        double shield = booked.shield(balance.slo());
        int reward = (int) Math.round(balance.waveReward() * shield);

        List<ActiveThreat> remaining = new ArrayList<>();
        for (ActiveThreat t : p.activeThreats()) {
            if (rules.content().threat(t.id()).persists()) {
                remaining.add(t);
            }
        }
        WaveResult result = new WaveResult(booked.number(), true, shield, reward - upkeep, null);
        return s.withProgress(review(p.withWave(booked.in(WaveState.Phase.COMPLETE)).withResult(result))
                .withCredits(p.credits() + reward - upkeep)
                .withActiveThreats(remaining));
    }

    /** The player's one answer to the open postmortem; the right root cause earns the bonus (decision AA). */
    GameState answer(GameState s, String optionId) {
        Progress p = s.progress();
        PostmortemState pm = p.postmortem();
        if (pm == null) {
            throw new IllegalArgumentException("no postmortem to answer");
        }
        if (pm.answered()) {
            throw new IllegalArgumentException("postmortem of wave " + pm.wave() + " is already answered");
        }
        ThreatDef.Option chosen = null;
        for (ThreatDef.Option o : rules.content().threat(pm.threatId()).postmortem().options()) {
            if (o.id().equals(optionId)) {
                chosen = o;
            }
        }
        if (chosen == null) {
            throw new IllegalArgumentException("unknown postmortem option: " + optionId);
        }
        int bonus = chosen.correct() ? rules.balance().postmortemBonus() : 0;
        // The threat's codex entries open now, not when it arrives: they would give the answer away.
        return s.withProgress(p.withPostmortem(pm.answer(optionId))
                .withPostmortemVerdict(pm.wave(), chosen.correct())
                .withCredits(p.credits() + bonus)
                .withCodex(rules.content().threat(pm.threatId()).codex()));
    }

    /** A threat wave that just ended opens its postmortem, unless that threat was already reviewed. */
    private Progress review(Progress ended) {
        ThreatDef threat = rules.threatForWave(ended.wave().number());
        if (threat == null || ended.reviewedThreats().contains(threat.id())) {
            return ended;
        }
        return ended.withPostmortem(new PostmortemState(threat.id(), ended.wave().number(), null));
    }

    /**
     * Share of requests slower than the latency SLO (decision AL), assuming exponentially distributed latency
     * with the pipeline's mean: P(L > slo) = e^(-slo / mean). The pipeline only knows mean waits of three
     * chained queues, not their distributions, so this is the same tail approximation as the p99 panel
     * (ADR-0013); for a single busy M/M/c queue Erlang C gives the same exponential decay. The share grows
     * smoothly: ~0 at a 25 ms mean (e^-20), a third when the mean reaches the SLO, nearly all under a growing backlog.
     */
    static double slowShare(double meanLatencyMs, double sloMs) {
        if (meanLatencyMs <= 0) {
            return 0;
        }
        double share = StrictMath.exp(-sloMs / meanLatencyMs);
        // The fluid model would otherwise book millionths of a request on a healthy station, which would
        // count as a symptom and keep the shield a hair below full.
        return share < NEGLIGIBLE_SHARE ? 0 : share;
    }

    private static OccupancyState occupancy(GameState s, ResourceId id) {
        return (OccupancyState) s.resource(id).state();
    }
}
