package com.nodezero.sim.engine;

import com.nodezero.sim.state.GameState;
import com.nodezero.sim.state.ResourceId;
import com.nodezero.sim.state.ResourceNode;
import com.nodezero.sim.state.WaveState;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

/**
 * The simulation kernel as a pure function: same (state, commands, tick) always yields the same next state.
 * {@link Game} adds the request pipeline on top via a {@link LoadPlanner}.
 */
public final class Simulation {

    private Simulation() {}

    /** Advances resources with loads set only by commands. */
    public static GameState step(GameState state, List<Command> commands, long tick) {
        return step(state, commands, tick, LoadPlanner.NONE);
    }

    /**
     * Computes the state at the end of {@code tick}: apply commands, plan loads, advance every model.
     *
     * @param tick must be {@code state.tick() + 1}; anything else means a replay or client is out of sync
     */
    public static GameState step(GameState state, List<Command> commands, long tick, LoadPlanner planner) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(commands, "commands");
        if (tick != state.tick() + 1) {
            throw new IllegalArgumentException("expected tick " + (state.tick() + 1) + " but got " + tick);
        }

        EnumMap<ResourceId, ResourceNode<?, ?>> nodes = new EnumMap<>(ResourceId.class);
        nodes.putAll(state.resources());
        WaveState wave = state.wave();

        for (Command command : commands) {
            switch (command) {
                case Command.Noop noop -> { }
                case Command.StartWave start -> wave = new WaveState(start.number(), tick);
                case Command.SetArrivalRate set ->
                        nodes.put(set.resource(), existing(nodes, set.resource()).withArrivalRps(set.rps()));
                case Command.SetHoldTime set ->
                        nodes.put(set.resource(), existing(nodes, set.resource()).withHoldTimeMs(set.holdTimeMs()));
            }
        }

        if (planner != LoadPlanner.NONE) {
            GameState planned = new GameState(state.tick(), state.seed(), wave, nodes);
            planner.plan(planned, tick).forEach((id, demand) -> nodes.put(id, existing(nodes, id).withDemand(demand)));
        }

        nodes.replaceAll((id, node) -> node.advance(SimConstants.DT));
        return new GameState(tick, state.seed(), wave, nodes);
    }

    private static ResourceNode<?, ?> existing(EnumMap<ResourceId, ResourceNode<?, ?>> nodes, ResourceId id) {
        ResourceNode<?, ?> node = nodes.get(id);
        if (node == null) {
            throw new IllegalArgumentException("station has no resource " + id);
        }
        return node;
    }
}
