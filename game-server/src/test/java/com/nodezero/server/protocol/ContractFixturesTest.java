package com.nodezero.server.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import com.nodezero.sim.engine.Command;
import com.nodezero.sim.engine.Game;
import com.nodezero.sim.engine.GameBalance;
import com.nodezero.sim.state.GameState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wire contract with the TypeScript client (slice 5, decision R). The server side owns
 * {@code client/src/protocol/fixtures/server/*.json}: one real message per type, which the client tests
 * parse strictly. The client side owns {@code fixtures/client/*.json}: what its encoder produces, which
 * must parse here. Changing either side without the other fails a test.
 * <p>Regenerate after an intended change (review the diff): {@code ./mvnw test -pl game-server -am
 * -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ContractFixturesTest -Dcontract.update=true}
 */
class ContractFixturesTest {

    private static final Path FIXTURES = Path.of("..", "client", "src", "protocol", "fixtures");
    private static final boolean UPDATE = Boolean.getBoolean("contract.update");

    private final JsonMapper json = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    private static Map<String, ServerMessage> serverMessages() {
        Game game = Game.chapterOne(GameBalance.PRACTICE);
        GameState s = game.newState(42);
        s = game.step(s, List.of(new Command.ApplyCounter("prometheus")), 1);
        s = game.step(s, List.of(new Command.ApplyCounter("grafana")), 2);
        for (long tick = 3; tick <= 300; tick++) {
            s = game.step(s, tick == 100 ? List.of(new Command.StartWave(1)) : List.of(), tick);
        }
        SnapshotStream stream = new SnapshotStream(10);
        ServerMessage.Snapshot keyframe = stream.next(game.observe(s));
        s = game.step(s, List.of(), 301);
        s = game.step(s, List.of(), 302);
        ServerMessage.Snapshot delta = stream.next(game.observe(s));

        Map<String, ServerMessage> messages = new LinkedHashMap<>();
        messages.put("welcome", new ServerMessage.Welcome("3f2b9c1e-0000-4000-8000-000000000001", 42, 10, 5));
        messages.put("snapshot-keyframe", keyframe);
        messages.put("snapshot-delta", delta);
        messages.put("rejected", new ServerMessage.Rejected(new ClientMessage.ApplyCounter("grafana", null),
                "grafana needs prometheus installed first"));
        messages.put("paused", new ServerMessage.Paused(true));
        messages.put("error", new ServerMessage.Error("cannot read message: Unexpected character"));
        return messages;
    }

    @Test
    void serverMessagesMatchTheClientFixtures() throws IOException {
        Path dir = FIXTURES.resolve("server");
        for (Map.Entry<String, ServerMessage> e : serverMessages().entrySet()) {
            // The pretty printer uses the platform line separator; fixtures are LF everywhere.
            String expected = json.writerFor(ServerMessage.class).writeValueAsString(e.getValue())
                    .replace("\r\n", "\n") + "\n";
            Path file = dir.resolve(e.getKey() + ".json");
            if (UPDATE) {
                Files.createDirectories(dir);
                Files.writeString(file, expected, StandardCharsets.UTF_8);
            }
            assertThat(file).as("fixture %s (regenerate with -Dcontract.update=true)", file).exists();
            assertThat(Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n"))
                    .as(e.getKey()).isEqualTo(expected);
        }
    }

    @Test
    void keyframeAndDeltaShowTheOptionalLists() {
        Map<String, ServerMessage> messages = serverMessages();
        ServerMessage.Snapshot keyframe = (ServerMessage.Snapshot) messages.get("snapshot-keyframe");
        ServerMessage.Snapshot delta = (ServerMessage.Snapshot) messages.get("snapshot-delta");

        assertThat(keyframe.counters()).containsExactly("prometheus", "grafana");
        assertThat(delta).isNotNull();
        assertThat(delta.counters()).isNull();
        assertThat(delta.insights()).isNull();
    }

    @Test
    void clientFixturesParseAsPlayerActions() throws IOException {
        Map<String, ClientMessage> expected = new LinkedHashMap<>();
        expected.put("start-wave", new ClientMessage.StartWave(1));
        expected.put("apply-counter", new ClientMessage.ApplyCounter("create-index", "concurrently"));
        expected.put("pause", new ClientMessage.Pause());
        expected.put("resume", new ClientMessage.Resume());

        for (Map.Entry<String, ClientMessage> e : expected.entrySet()) {
            Path file = FIXTURES.resolve("client").resolve(e.getKey() + ".json");
            JsonNode raw = json.readTree(Files.readString(file, StandardCharsets.UTF_8));
            assertThat(json.treeToValue(raw, ClientMessage.class)).as(e.getKey()).isEqualTo(e.getValue());
        }
    }
}
