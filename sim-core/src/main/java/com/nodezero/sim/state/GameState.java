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
public record GameState(long tick, long seed, Progress progress, Map<ResourceId, ResourceNode<?, ?>> resources) {

    public GameState {
        if (tick < 0) {
            throw new IllegalArgumentException("tick must be >= 0: " + tick);
        }
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(resources, "resources");
        EnumMap<ResourceId, ResourceNode<?, ?>> copy = new EnumMap<>(ResourceId.class);
        resources.forEach((id, node) -> copy.put(
                Objects.requireNonNull(id, "resource id"), Objects.requireNonNull(node, "resource node")));
        resources = Collections.unmodifiableMap(copy);
    }

    /** Resources only, no game progress (kernel-level tests). */
    public GameState(long tick, long seed, Map<ResourceId, ResourceNode<?, ?>> resources) {
        this(tick, seed, Progress.NONE, resources);
    }

    public WaveState wave() {
        return progress.wave();
    }

    public GameState withProgress(Progress newProgress) {
        return new GameState(tick, seed, newProgress, resources);
    }

    public GameState withResources(Map<ResourceId, ResourceNode<?, ?>> newResources) {
        return new GameState(tick, seed, progress, newResources);
    }

    public ResourceNode<?, ?> resource(ResourceId id) {
        ResourceNode<?, ?> node = resources.get(id);
        if (node == null) {
            throw new IllegalArgumentException("station has no resource " + id);
        }
        return node;
    }
}
