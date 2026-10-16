package com.nodezero.sim.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nodezero.sim.model.Load;
import com.nodezero.sim.model.UtilizationModel;
import com.nodezero.sim.model.UtilizationState;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GameStateTest {

    private static ResourceNode<UtilizationState> node() {
        return new ResourceNode<>(new UtilizationModel(4, 50, 100), new Load(0), UtilizationState.initial());
    }

    @Test
    void resourcesCannotBeModifiedFromOutside() {
        // Mutable source on purpose: Map.of would be unmodifiable already and hide a missing copy.
        Map<ResourceId, ResourceNode<?>> source = new LinkedHashMap<>();
        source.put(ResourceId.CPU, node());
        GameState state = new GameState(0, 42, source);

        assertThatThrownBy(() -> state.resources().put(ResourceId.DISK, node()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void takesDefensiveCopyOfSourceMap() {
        Map<ResourceId, ResourceNode<?>> source = new LinkedHashMap<>();
        source.put(ResourceId.CPU, node());
        GameState state = new GameState(0, 42, source);

        source.put(ResourceId.DISK, node());

        assertThat(state.resources()).containsOnlyKeys(ResourceId.CPU);
    }

    @Test
    void iteratesResourcesInDeclarationOrder() {
        Map<ResourceId, ResourceNode<?>> source = new LinkedHashMap<>();
        source.put(ResourceId.DISK, node());
        source.put(ResourceId.CPU, node());

        GameState state = new GameState(0, 42, source);

        assertThat(state.resources().keySet()).containsExactly(ResourceId.CPU, ResourceId.DISK);
    }

    @Test
    void rejectsNegativeTick() {
        assertThatThrownBy(() -> new GameState(-1, 42, Map.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
