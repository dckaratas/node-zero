package com.nodezero.sim.state;

import com.nodezero.sim.model.Demand;
import com.nodezero.sim.model.HoldLoad;
import com.nodezero.sim.model.Load;
import com.nodezero.sim.model.ResourceModel;
import com.nodezero.sim.model.ResourceState;
import java.util.Objects;

/**
 * One resource of the station: its model (configuration), current load and last computed state.
 * Keeping all three together makes {@link GameState} self-contained for replays.
 */
public record ResourceNode<S extends ResourceState, L extends Load<L>>(ResourceModel<S, L> model, L load, S state) {

    public ResourceNode {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(load, "load");
        Objects.requireNonNull(state, "state");
    }

    public ResourceNode<S, L> withArrivalRps(double arrivalRps) {
        return new ResourceNode<>(model, load.withArrivalRps(arrivalRps), state);
    }

    public ResourceNode<S, L> withHoldTimeMs(double holdTimeMs) {
        if (!(load instanceof HoldLoad hold)) {
            throw new IllegalArgumentException("resource load has no hold time: " + load);
        }
        // Safe: load is an L whose runtime class is the final record HoldLoad, so L is HoldLoad.
        @SuppressWarnings("unchecked")
        L updated = (L) hold.withHoldTimeMs(holdTimeMs);
        return new ResourceNode<>(model, updated, state);
    }

    public ResourceNode<S, L> withDemand(Demand demand) {
        return new ResourceNode<>(model, model.loadFor(demand), state);
    }

    public ResourceNode<S, L> advance(double dt) {
        return new ResourceNode<>(model, load, model.step(state, load, dt));
    }
}
