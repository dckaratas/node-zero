package com.nodezero.sim.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.nodezero.sim.engine.Command.Noop;
import com.nodezero.sim.engine.Command.SetArrivalRate;
import com.nodezero.sim.engine.Command.SetHoldTime;
import com.nodezero.sim.model.HoldLoad;
import com.nodezero.sim.model.OccupancyModel;
import com.nodezero.sim.model.OccupancyState;
import com.nodezero.sim.model.RateLoad;
import com.nodezero.sim.model.UtilizationModel;
import com.nodezero.sim.model.UtilizationState;
import com.nodezero.sim.state.GameState;
import com.nodezero.sim.state.ResourceId;
import com.nodezero.sim.state.ResourceNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SimulationTest {

    private static GameState idleStation() {
        ResourceNode<UtilizationState, RateLoad> cpu =
                new ResourceNode<>(new UtilizationModel(4, 50, 100), new RateLoad(0), UtilizationState.initial());
        return new GameState(0, 42, Map.of(ResourceId.CPU, cpu));
    }

    private static UtilizationState cpu(GameState state) {
        return (UtilizationState) state.resources().get(ResourceId.CPU).state();
    }

    @Test
    void advancesTickByOne() {
        GameState next = Simulation.step(idleStation(), List.of(), 1);

        assertThat(next.tick()).isEqualTo(1);
        assertThat(next.seed()).isEqualTo(42);
    }

    @Test
    void rejectsSkippedOrRepeatedTick() {
        GameState start = idleStation();

        assertThatThrownBy(() -> Simulation.step(start, List.of(), 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Simulation.step(start, List.of(), 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void appliesCommandsBeforeAdvancingModels() {
        GameState next = Simulation.step(idleStation(), List.of(new SetArrivalRate(ResourceId.CPU, 100)), 1);

        assertThat(cpu(next).rho()).isCloseTo(0.5, within(1e-9));
        assertThat(cpu(next).latencyMs()).isCloseTo(500.0 / 23, within(1e-9));
    }

    @Test
    void loadPersistsAcrossTicks() {
        GameState t1 = Simulation.step(idleStation(), List.of(new SetArrivalRate(ResourceId.CPU, 300)), 1);
        GameState t2 = Simulation.step(t1, List.of(new Noop()), 2);

        assertThat(cpu(t2).queue()).isCloseTo(20.0, within(1e-9));
    }

    @Test
    void rejectsCommandForMissingResource() {
        List<Command> commands = List.of(new SetArrivalRate(ResourceId.DISK, 10));

        assertThatThrownBy(() -> Simulation.step(idleStation(), commands, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void slowQueryCommandExhaustsThePool() {
        ResourceNode<OccupancyState, HoldLoad> pool =
                new ResourceNode<>(new OccupancyModel(10, 100), new HoldLoad(100, 50), OccupancyState.initial());
        GameState start = new GameState(0, 42, Map.of(ResourceId.DB_POOL, pool));

        GameState next = Simulation.step(start, List.of(new SetHoldTime(ResourceId.DB_POOL, 500)), 1);

        OccupancyState db = (OccupancyState) next.resources().get(ResourceId.DB_POOL).state();
        assertThat(db.active()).isCloseTo(10.0, within(1e-9));
        assertThat(db.backlog()).isCloseTo(8.0, within(1e-9));
    }

    @Test
    void rejectsHoldTimeForRateResource() {
        List<Command> commands = List.of(new SetHoldTime(ResourceId.CPU, 500));

        assertThatThrownBy(() -> Simulation.step(idleStation(), commands, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void doesNotChangeInputState() {
        GameState start = idleStation();
        GameState snapshot = idleStation();

        Simulation.step(start, List.of(new SetArrivalRate(ResourceId.CPU, 300)), 1);

        assertThat(start).isEqualTo(snapshot);
    }

    @Test
    void sameInputsProduceIdenticalStates() {
        assertThat(replay()).isEqualTo(replay());
    }

    private static GameState replay() {
        GameState state = idleStation();
        for (long tick = 1; tick <= 200; tick++) {
            List<Command> commands = switch ((int) tick) {
                case 1 -> List.of(new SetArrivalRate(ResourceId.CPU, 120));
                case 50 -> List.of(new SetArrivalRate(ResourceId.CPU, 320));
                case 120 -> List.of(new SetArrivalRate(ResourceId.CPU, 80));
                default -> List.of();
            };
            state = Simulation.step(state, commands, tick);
        }
        return state;
    }
}
