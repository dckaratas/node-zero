package com.nodezero.sim.engine;

import com.nodezero.sim.model.Demand;
import com.nodezero.sim.state.GameState;
import com.nodezero.sim.state.ResourceId;
import com.nodezero.sim.state.ResourceNode;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The simulation kernel as pure functions: same inputs always yield the same next state.
 * {@link Game} composes these with the request pipeline, waves and counters.
 */
public final class Simulation {

    private Simulation() {}

    /** One tick with loads set only by resource commands (kernel-level tests and tools). */
    public static GameState step(GameState state, List<Command> commands, long tick) {
        requireNextTick(state, tick);
        return advance(applyCommands(state, commands), Collections.emptyMap(), tick);
    }

    /** @throws IllegalArgumentException unless {@code tick == state.tick() + 1} (replay or client out of sync) */
    public static void requireNextTick(GameState state, long tick) {
        Objects.requireNonNull(state, "state");
        if (tick != state.tick() + 1) {
            throw new IllegalArgumentException("expected tick " + (state.tick() + 1) + " but got " + tick);
        }
    }

    /** Applies resource commands; the tick does not advance. */
    public static GameState applyCommands(GameState state, List<Command> commands) {
        Objects.requireNonNull(commands, "commands");
        if (commands.isEmpty()) {
            return state;
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
                case Command.StartWave c -> throw gameCommand(c);
                case Command.ApplyCounter c -> throw gameCommand(c);
                case Command.AnswerPostmortem c -> throw gameCommand(c);
                case Command.RunAction c -> throw gameCommand(c);
                case Command.RemoveCounter c -> throw gameCommand(c);
                case Command.ConfigureCounter c -> throw gameCommand(c);
                case Command.UnlockInsight c -> throw gameCommand(c);
            }
        }
        return state.withResources(nodes);
    }

    /** Sets the given demands, then advances every resource model by one tick. */
    public static GameState advance(GameState state, Map<ResourceId, Demand> demands, long tick) {
        EnumMap<ResourceId, ResourceNode<?, ?>> nodes = new EnumMap<>(ResourceId.class);
        nodes.putAll(state.resources());
        demands.forEach((id, demand) -> nodes.put(id, existing(nodes, id).withDemand(demand)));
        nodes.replaceAll((id, node) -> node.advance(SimConstants.DT));
        return new GameState(tick, state.seed(), state.progress(), nodes);
    }

    private static IllegalArgumentException gameCommand(Command command) {
        return new IllegalArgumentException("game command needs Game.step: " + command);
    }

    private static ResourceNode<?, ?> existing(EnumMap<ResourceId, ResourceNode<?, ?>> nodes, ResourceId id) {
        ResourceNode<?, ?> node = nodes.get(id);
        if (node == null) {
            throw new IllegalArgumentException("station has no resource " + id);
        }
        return node;
    }
}
