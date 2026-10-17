package com.nodezero.sim.state;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable snapshot of the whole simulation at the end of {@code tick}.
 *
 * <p>Resources are copied into an {@link EnumMap} so iteration follows {@link ResourceId}
 * declaration order on every JVM (ADR-0012).
 */
public record GameState(long tick, long seed, WaveState wave, Map<ResourceId, ResourceNode<?, ?>> resources) {

    public GameState {
        if (tick < 0) {
            throw new IllegalArgumentException("tick must be >= 0: " + tick);
        }
        Objects.requireNonNull(wave, "wave");
        Objects.requireNonNull(resources, "resources");
        EnumMap<ResourceId, ResourceNode<?, ?>> copy = new EnumMap<>(ResourceId.class);
        resources.forEach((id, node) -> copy.put(
                Objects.requireNonNull(id, "resource id"), Objects.requireNonNull(node, "resource node")));
        resources = Collections.unmodifiableMap(copy);
    }

    /** State in the preparation phase (no wave running). */
    public GameState(long tick, long seed, Map<ResourceId, ResourceNode<?, ?>> resources) {
        this(tick, seed, WaveState.PREPARATION, resources);
    }

    public ResourceNode<?, ?> resource(ResourceId id) {
        ResourceNode<?, ?> node = resources.get(id);
        if (node == null) {
            throw new IllegalArgumentException("station has no resource " + id);
        }
        return node;
    }
}
