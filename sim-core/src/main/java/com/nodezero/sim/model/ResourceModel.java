package com.nodezero.sim.model;

/** A pure function from (previous state, load, dt) to the next state. */
public sealed interface ResourceModel<S extends ResourceState> permits UtilizationModel {

    S step(S previous, Load load, double dt);
}
