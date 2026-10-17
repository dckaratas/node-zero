package com.nodezero.sim.engine;

import com.nodezero.sim.content.ContentLoader;
import com.nodezero.sim.state.GameState;
import com.nodezero.sim.state.Progress;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-authoritative game step. Pure: the same (state, commands, tick) always gives the same result.
 * Immutable and thread-safe; one instance can serve every session that uses the same rules.
 *
 * <p>Order within a tick: game commands → resource commands → counters becoming active →
 * request pipeline → models advance → error budget and wave bookkeeping.
 */
public final class Game {

    private final Rules rules;
    private final Pipeline pipeline;
    private final Waves waves;
    private final Counters counters;

    public Game(Rules rules) {
        this.rules = rules;
        this.pipeline = new Pipeline(rules);
        this.waves = new Waves(rules);
        this.counters = new Counters(rules);
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
        return rules.station().initialState(seed).withProgress(Progress.start(rules.balance().startCredits()));
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
        return waves.account(s, plan, tick);
    }

    private static List<Command> append(List<Command> list, Command command) {
        List<Command> next = new ArrayList<>(list);
        next.add(command);
        return next;
    }
}
