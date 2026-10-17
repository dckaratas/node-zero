package com.nodezero.sim.engine;

import com.nodezero.sim.model.Demand;
import com.nodezero.sim.state.GameState;
import com.nodezero.sim.state.ResourceId;
import java.util.EnumMap;
import java.util.Map;

/** Decides each resource's demand for a tick, after commands were applied and before models advance. */
@FunctionalInterface
public interface LoadPlanner {

    /** Keeps loads as they are; resources are driven by commands only (low-level tests). */
    LoadPlanner NONE = (state, tick) -> new EnumMap<>(ResourceId.class);

    /**
     * @param state previous tick's resource states with this tick's commands applied
     * @param tick the tick being computed
     */
    Map<ResourceId, Demand> plan(GameState state, long tick);
}
