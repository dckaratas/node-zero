package com.nodezero.sim.engine;

import com.nodezero.sim.content.ContentLoader;
import com.nodezero.sim.state.GameState;
import java.util.List;

/**
 * Server-authoritative game step: {@link Simulation} plus the request pipeline driven by content.
 * Immutable and thread-safe; one instance can serve every session that uses the same rules.
 */
public final class Game {

    private final Rules rules;
    private final Pipeline pipeline;

    public Game(Rules rules) {
        this.rules = rules;
        this.pipeline = new Pipeline(rules);
    }

    /** NODE-01 with the shipped content. */
    public static Game chapterOne() {
        return new Game(new Rules(StationLayout.node01(), ContentLoader.loadDefault()));
    }

    public Rules rules() {
        return rules;
    }

    public GameState newState(long seed) {
        return rules.station().initialState(seed);
    }

    public GameState step(GameState state, List<Command> commands, long tick) {
        return Simulation.step(state, commands, tick, pipeline);
    }
}
