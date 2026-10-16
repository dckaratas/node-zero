package com.nodezero.sim.model;

/** Per-tick output of a {@link ResourceModel}. */
public sealed interface ResourceState permits UtilizationState {}
