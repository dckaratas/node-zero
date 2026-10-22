package com.nodezero.sim.engine;

import com.nodezero.sim.content.ContentLoader;
import com.nodezero.sim.state.GameState;
import com.nodezero.sim.state.OpsState;
import com.nodezero.sim.state.Progress;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-authoritative game step. Pure: the same (state, commands, tick) always gives the same result.
 * Immutable and thread-safe; one instance can serve every session that uses the same rules.
 *
 * <p>Order within a tick: game commands (incl. incident actions) → resource commands → counters becoming active →
 * request pipeline → models advance → error budget and wave bookkeeping → alert rules.
 */
public final class Game {

    private final Rules rules;
    private final Pipeline pipeline;
    private final Waves waves;
    private final Counters counters;
    private final Actions actions;
    private final Alerts alerts;
    private final Observer observer;

    public Game(Rules rules) {
        this.rules = rules;
        this.pipeline = new Pipeline(rules);
        this.waves = new Waves(rules);
        this.counters = new Counters(rules);
        this.actions = new Actions(rules);
        this.alerts = new Alerts(rules);
        this.observer = new Observer(rules);
    }

    /** NODE-01 with the shipped content. */
    public static Game chapterOne() {
        return chapterOne(GameBalance.DEFAULT);
    }

    public static Game chapterOne(GameBalance balance) {
        return new Game(new Rules(StationLayout.node01(), ContentLoader.loadDefault(), balance));
    }

    public Rules rules() {
        return rules;
    }

    public GameState newState(long seed) {
        GameBalance balance = rules.balance();
        return rules.station().initialState(seed)
                .withProgress(Progress.start(balance.startCredits()).withOps(OpsState.start(balance.crewPerShift())));
    }

    /** @throws IllegalArgumentException on an out-of-sync tick or a command the rules do not allow */
    public GameState step(GameState state, List<Command> commands, long tick) {
        Simulation.requireNextTick(state, tick);
        GameState s = state;
        List<Command> resourceCommands = List.of();
        for (Command command : commands) {
            switch (command) {
                case Command.StartWave start -> s = waves.start(s, start.number(), tick);
                case Command.ApplyCounter apply -> s = counters.apply(s, apply, tick);
                case Command.AnswerPostmortem answer -> s = waves.answer(s, answer.optionId());
                case Command.RunAction run -> s = actions.run(s, run, tick);
                case Command.RemoveCounter remove -> s = counters.remove(s, remove, tick);
                case Command.ConfigureCounter configure -> s = counters.configure(s, configure, tick);
                case Command.SetAlertRule set -> s = alerts.set(s, set, tick);
                case Command.RemoveAlertRule remove -> s = alerts.remove(s, remove);
                case Command.UnlockInsight unlock -> s = s.withProgress(s.progress().withInsight(unlock.insightId()));
                case Command.Noop noop -> { }
                case Command.SetArrivalRate c -> resourceCommands = append(resourceCommands, c);
                case Command.SetHoldTime c -> resourceCommands = append(resourceCommands, c);
            }
        }
        s = Simulation.applyCommands(s, resourceCommands);
        s = counters.activate(s, tick);
        Plan plan = pipeline.plan(s, tick);
        s = Simulation.advance(s, plan.demands(), tick);
        return alerts.evaluate(waves.account(s, plan, tick), tick);
    }

    /** The player's view of {@code state}: only what their monitoring can see (fog of war). */
    public Observation observe(GameState state) {
        return observer.observe(state);
    }

    private static List<Command> append(List<Command> list, Command command) {
        List<Command> next = new ArrayList<>(list);
        next.add(command);
        return next;
    }
}
