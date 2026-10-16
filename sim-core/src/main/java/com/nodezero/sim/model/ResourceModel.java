package com.nodezero.sim.model;

/** A pure function from (previous state, load, dt) to the next state. */
public sealed interface ResourceModel<S extends ResourceState, L extends Load<L>>
        permits UtilizationModel, OccupancyModel {

    S step(S previous, L load, double dt);
}
