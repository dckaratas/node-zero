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

    /** Starts wave {@code number} at this tick; the threat whose trigger.wave matches drives traffic. */
    record StartWave(int number) implements Command {

        public StartWave {
            if (number < 1) {
                throw new IllegalArgumentException("wave number must be >= 1: " + number);
            }
        }
    }

    /** Sets how long each request holds a slot of a pool resource, e.g. a query slowing down (wave 4). */
    record SetHoldTime(ResourceId resource, double holdTimeMs) implements Command {

        public SetHoldTime {
            Objects.requireNonNull(resource, "resource");
        }
    }
}
