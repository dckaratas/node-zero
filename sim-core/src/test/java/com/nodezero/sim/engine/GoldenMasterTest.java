package com.nodezero.sim.engine;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Golden master: a fixed scenario must reproduce the recorded states exactly, on every JVM and OS
 * (ADR-0012). Running twice inside one JVM cannot catch per-JVM effects such as Set.of salting;
 * a file committed from one machine and checked by CI on others can.
 *
 * <p>When a formula changes on purpose, regenerate and review the diff:
 * {@code ./mvnw test -Dtest=GoldenMasterTest -Dgolden.update=true}
 */
class GoldenMasterTest {

    private static final Path DIR = Path.of("src", "test", "resources", "golden");

    @Test
    void replayMatchesRecordedStates() throws IOException {
        assertMatchesGolden("station-replay.txt", replay());
    }

    @Test
    void chapterOneWaveFourMatchesRecordedStates() throws IOException {
        Game game = Game.chapterOne();
        GameState state = game.newState(2187);
        List<String> lines = new ArrayList<>();
        for (long tick = 1; tick <= 1500; tick++) {
            state = game.step(state, tick == 1 ? List.of(new Command.StartWave(4)) : List.of(), tick);
            if (tick % 100 == 0) {
                lines.add("tick=" + tick + " " + state.resources());
            }
        }
        assertMatchesGolden("wave-4-replay.txt", String.join("\n", lines) + "\n");
    }

    private static void assertMatchesGolden(String name, String actual) throws IOException {
        Path golden = DIR.resolve(name);
        if (Boolean.getBoolean("golden.update")) {
            Files.createDirectories(DIR);
            Files.writeString(golden, actual, StandardCharsets.UTF_8);
        }

        assertThat(golden).as("golden file missing, regenerate with -Dgolden.update=true").exists();
        assertThat(Files.readString(golden, StandardCharsets.UTF_8)).isEqualTo(actual);
    }

    /** Wave 4 rehearsal: traffic rises, a query slows down at tick 100 and is fixed at tick 200. */
    private static String replay() {
        GameState state = station();
        List<String> lines = new ArrayList<>();
        for (long tick = 1; tick <= 300; tick++) {
            List<Command> commands = switch ((int) tick) {
                case 20 -> List.of(new SetArrivalRate(ResourceId.CPU, 170), new SetArrivalRate(ResourceId.DB_POOL, 120));
                case 100 -> List.of(new SetHoldTime(ResourceId.DB_POOL, 400));
                case 200 -> List.of(new SetHoldTime(ResourceId.DB_POOL, 50));
                case 250 -> List.of(new SetArrivalRate(ResourceId.CPU, 260));
                default -> List.of();
            };
            state = Simulation.step(state, commands, tick);
            if (tick % 50 == 0) {
                lines.add("tick=" + tick + " " + state.resources());
            }
        }
        return String.join("\n", lines) + "\n";
    }

    private static GameState station() {
        Map<ResourceId, ResourceNode<?, ?>> resources = new LinkedHashMap<>();
        resources.put(ResourceId.CPU, new ResourceNode<>(
                new UtilizationModel(4, 50, 100), new RateLoad(100), UtilizationState.initial()));
        resources.put(ResourceId.DB_POOL, new ResourceNode<>(
                new OccupancyModel(10, 100), new HoldLoad(80, 50), OccupancyState.initial()));
        return new GameState(0, 2187, resources);
    }
}
