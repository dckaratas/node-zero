package com.nodezero.sim.engine;

import com.nodezero.sim.state.GameState;
import com.nodezero.sim.state.ResourceId;
import com.nodezero.sim.state.ResourceNode;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

/** The simulation as a pure function: same (state, commands, tick) always yields the same next state. */
public final class Simulation {

    private Simulation() {}

    /**
     * Computes the state at the end of {@code tick}.
     *
     * @param tick must be {@code state.tick() + 1}; anything else means a replay or client is out of sync
     */
    public static GameState step(GameState state, List<Command> commands, long tick) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(commands, "commands");
        if (tick != state.tick() + 1) {
            throw new IllegalArgumentException("expected tick " + (state.tick() + 1) + " but got " + tick);
        }

        EnumMap<ResourceId, ResourceNode<?, ?>> nodes = new EnumMap<>(ResourceId.class);
        nodes.putAll(state.resources());

        for (Command command : commands) {
            switch (command) {
                case Command.Noop noop -> { }
                case Command.SetArrivalRate set ->
                        nodes.put(set.resource(), existing(nodes, set.resource()).withArrivalRps(set.rps()));
                case Command.SetHoldTime set ->
                        nodes.put(set.resource(), existing(nodes, set.resource()).withHoldTimeMs(set.holdTimeMs()));
            }
        }

        nodes.replaceAll((id, node) -> node.advance(SimConstants.DT));
        return new GameState(tick, state.seed(), nodes);
    }

    private static ResourceNode<?, ?> existing(EnumMap<ResourceId, ResourceNode<?, ?>> nodes, ResourceId id) {
        ResourceNode<?, ?> node = nodes.get(id);
        if (node == null) {
            throw new IllegalArgumentException("station has no resource " + id);
        }
        return node;
    }
}
