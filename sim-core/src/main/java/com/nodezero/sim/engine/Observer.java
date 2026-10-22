package com.nodezero.sim.engine;

import com.nodezero.sim.content.ThreatDef;
import com.nodezero.sim.model.FillModel;
import com.nodezero.sim.model.OccupancyModel;
import com.nodezero.sim.model.UtilizationModel;
import com.nodezero.sim.state.GameState;
import com.nodezero.sim.state.PostmortemState;
import com.nodezero.sim.state.ResourceId;
import com.nodezero.sim.state.Progress;
import com.nodezero.sim.state.WaveState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the full simulation state into what a player may see (fog of war, decision D).
 * The simulation always computes everything; only this view is filtered, on the server.
 */
final class Observer {

    /** Users complain after this long of continuous failures in a wave without a threat of its own. */
    private static final double DEFAULT_COMPLAINT_DELAY_S = 30;
    private static final String DEFAULT_SIGNAL = "user_complaints";

    private final Rules rules;

    Observer(Rules rules) {
        this.rules = rules;
    }

    Observation observe(GameState s) {
        Progress p = s.progress();
        WaveState wave = p.wave();
        Monitoring modules = Monitoring.at(rules, p, s.tick());

        Map<String, Double> metrics = new LinkedHashMap<>();
        for (Metric metric : Metric.values()) {
            if (modules.allow(metric.tier())) {
                metrics.put(metric.metricName(), metric.read(s));
            }
        }

        List<String> signals = new ArrayList<>();
        if (wave.phase() == WaveState.Phase.RUNNING && wave.symptomSinceTick() >= 0) {
            ThreatDef threat = rules.threatForWave(wave.number());
            String signal = threat == null ? DEFAULT_SIGNAL : threat.visibility().withoutMonitoring().signal();
            double delayS = threat == null ? DEFAULT_COMPLAINT_DELAY_S : threat.visibility().withoutMonitoring().delayS();
            if ((s.tick() - wave.symptomSinceTick()) * SimConstants.DT >= delayS) {
                signals.add(signal);
            }
        }

        List<Observation.Counter> counters = new ArrayList<>();
        p.counters().forEach(c -> counters.add(
                new Observation.Counter(c.id(), c.option(), c.activeTick(), c.removedTick(), c.setting())));
        double slo = rules.balance().slo();
        double shield = wave.shield(slo);
        double burnRate = 0;
        double emptyInS = -1;
        if (wave.phase() == WaveState.Phase.RUNNING && wave.lastFailedRps() > 0) {
            // The budget lasts the whole SLO window at allowance / window failures per second (burn rate 1).
            double allowance = (1 - slo) * wave.windowRequests();
            double windowS = rules.balance().budgetWindowS();
            if (allowance > 0 && windowS > 0) {
                burnRate = wave.lastFailedRps() / (allowance / windowS);
                emptyInS = shield * allowance / wave.lastFailedRps();
            }
        }
        Observation.Chapter chapter = ChapterScore.summary(rules.chapter(), rules.lastWave(), p.results(),
                p.postmortem());
        return new Observation(s.tick(), p.credits(), wave.number(), wave.phase(), shield, burnRate, emptyInS,
                metrics, signals, p.insights(), counters, postmortem(p), p.codex(), p.results(), chapter,
                p.ops().crew(), p.ops().runs(), p.ops().alerts(), station(s));
    }

    private static Observation.Station station(GameState s) {
        return new Observation.Station(
                ((UtilizationModel) s.resource(ResourceId.CPU).model()).servers(),
                ((OccupancyModel) s.resource(ResourceId.THREADS).model()).poolSize(),
                ((OccupancyModel) s.resource(ResourceId.DB_POOL).model()).poolSize(),
                ((FillModel) s.resource(ResourceId.DISK).model()).capacity(),
                ((UtilizationModel) s.resource(ResourceId.DB_CPU).model()).servers(),
                ((UtilizationModel) s.resource(ResourceId.DB_IO).model()).servers());
    }

    private Observation.Postmortem postmortem(Progress p) {
        PostmortemState pm = p.postmortem();
        if (pm == null) {
            return null;
        }
        ThreatDef.Postmortem def = rules.content().threat(pm.threatId()).postmortem();
        List<Observation.Choice> options = new ArrayList<>();
        String correct = null;
        for (ThreatDef.Option o : def.options()) {
            options.add(new Observation.Choice(o.id(), o.key()));
            if (o.correct()) {
                correct = o.id();
            }
        }
        return new Observation.Postmortem(pm.wave(), def.questionKey(), options, pm.chosenOption(),
                pm.answered() ? correct : null);
    }
}
