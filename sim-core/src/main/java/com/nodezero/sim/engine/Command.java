package com.nodezero.sim.engine;

import com.nodezero.sim.state.ResourceId;
import java.util.Objects;

/**
 * Inputs applied at the start of a tick. Resource commands are handled by {@link Simulation};
 * game commands need rules and content and are handled by {@link Game}.
 */
public sealed interface Command {

    record Noop() implements Command {}

    /** Sets the arrival rate of a resource until changed again (kernel tests; the pipeline overrides it). */
    record SetArrivalRate(ResourceId resource, double rps) implements Command {

        public SetArrivalRate {
            Objects.requireNonNull(resource, "resource");
        }
    }

    /** Sets how long each request holds a slot of a pool resource (kernel tests). */
    record SetHoldTime(ResourceId resource, double holdTimeMs) implements Command {

        public SetHoldTime {
            Objects.requireNonNull(resource, "resource");
        }
    }

    /** Game: starts wave {@code number} at this tick; the threat whose trigger.wave matches arrives. */
    record StartWave(int number) implements Command {

        public StartWave {
            if (number < 1) {
                throw new IllegalArgumentException("wave number must be >= 1: " + number);
            }
        }
    }

    /** Game: buys and installs a counter, optionally choosing one of its options. */
    record ApplyCounter(String counterId, String optionId) implements Command {

        public ApplyCounter {
            Objects.requireNonNull(counterId, "counterId");
        }

        public ApplyCounter(String counterId) {
            this(counterId, null);
        }
    }

    /** Game: the on-call crew runs an incident action (slice 7b), optionally one of its options. */
    record RunAction(String actionId, String optionId) implements Command {

        public RunAction {
            Objects.requireNonNull(actionId, "actionId");
        }

        public RunAction(String actionId) {
            this(actionId, null);
        }
    }

    /** Game: the player's answer to the open postmortem (one attempt; ADR-0007). */
    record AnswerPostmortem(String optionId) implements Command {

        public AnswerPostmortem {
            Objects.requireNonNull(optionId, "optionId");
        }
    }

    /** Game: the player gained an insight (e.g. pg-stat-statements) that some counters require. */
    record UnlockInsight(String insightId) implements Command {

        public UnlockInsight {
            Objects.requireNonNull(insightId, "insightId");
        }
    }
}
