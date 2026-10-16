package com.nodezero.sim.engine;

import com.nodezero.sim.state.ResourceId;
import java.util.Objects;

/** Inputs applied at the start of a tick, before resource models advance. */
public sealed interface Command {

    record Noop() implements Command {}

    /** Sets the arrival rate of a resource until changed again. Used by tests and, later, the traffic system. */
    record SetArrivalRate(ResourceId resource, double rps) implements Command {

        public SetArrivalRate {
            Objects.requireNonNull(resource, "resource");
        }
    }
}
