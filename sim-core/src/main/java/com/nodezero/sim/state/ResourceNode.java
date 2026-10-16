package com.nodezero.sim.state;

import com.nodezero.sim.model.Load;
import com.nodezero.sim.model.ResourceModel;
import com.nodezero.sim.model.ResourceState;
import java.util.Objects;

/**
 * One resource of the station: its model (configuration), current load and last computed state.
 * Keeping all three together makes {@link GameState} self-contained for replays.
 */
public record ResourceNode<S extends ResourceState>(ResourceModel<S> model, Load load, S state) {

    public ResourceNode {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(load, "load");
        Objects.requireNonNull(state, "state");
    }

    public ResourceNode<S> withLoad(Load newLoad) {
        return new ResourceNode<>(model, newLoad, state);
    }

    public ResourceNode<S> advance(double dt) {
        return new ResourceNode<>(model, load, model.step(state, load, dt));
    }
}
