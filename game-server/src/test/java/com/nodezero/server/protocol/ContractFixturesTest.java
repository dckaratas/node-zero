package com.nodezero.server.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import com.nodezero.sim.content.Language;
import com.nodezero.sim.engine.Command;
import com.nodezero.sim.engine.Game;
import com.nodezero.sim.engine.GameBalance;
import com.nodezero.sim.engine.Observation;
import com.nodezero.sim.state.GameState;
import com.nodezero.sim.state.WaveState;
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
        messages.put("snapshot-postmortem", ServerMessage.Snapshot.full(game.observe(afterLogStorm(game))));
        messages.put("snapshot-burning", ServerMessage.Snapshot.full(game.observe(midSurge())));
        messages.put("snapshot-chapter", ServerMessage.Snapshot.full(game.observe(chapterWon(game))));
        messages.put("snapshot-incident", ServerMessage.Snapshot.full(game.observe(restartedDuringStorm(game))));
        messages.put("rejected", new ServerMessage.Rejected(new ClientMessage.ApplyCounter("grafana", null),
                "grafana needs prometheus installed first"));
        messages.put("paused", new ServerMessage.Paused(true));
        messages.put("error", new ServerMessage.Error("cannot read message: Unexpected character"));
        return messages;
    }

    /** No monitoring, wave 2 (log-storm) played out: the postmortem is open and its answer hidden. */
    private static GameState afterLogStorm(Game game) {
        GameState s = game.step(game.newState(7), List.of(new Command.StartWave(2)), 1);
        while (s.wave().phase() == WaveState.Phase.RUNNING) {
            s = game.step(s, List.of(), s.tick() + 1);
        }
        return s;
    }

    /** No counters, wave 3 (traffic surge) on the default rules: the shield burns fast. */
    private static GameState midSurge() {
        Game game = Game.chapterOne(GameBalance.DEFAULT);
        GameState s = game.step(game.newState(7), List.of(new Command.StartWave(3)), 1);
        while (game.observe(s).burnRate() < 5 && s.wave().phase() == WaveState.Phase.RUNNING) {
            s = game.step(s, List.of(), s.tick() + 1);
        }
        return s;
    }

    /** Wave 2 (log storm): the crew restarted the service a few seconds ago and deleted the log the wrong way. */
    private static GameState restartedDuringStorm(Game game) {
        GameState s = game.step(game.newState(7), List.of(new Command.StartWave(2)), 1);
        for (long tick = 2; tick <= 40; tick++) {
            List<Command> commands = switch ((int) tick) {
                case 20 -> List.of(new Command.RunAction("clear-disk", "rm"));
                case 30 -> List.of(new Command.RunAction("restart-app"));
                default -> List.of();
            };
            s = game.step(s, commands, tick);
        }
        return s;
    }

    /** All five waves played in practice mode, every postmortem answered right: the chapter summary. */
    private static GameState chapterWon(Game game) {
        String[] right = {null, null, "b", "b", "c", "c"};
        GameState s = game.newState(7);
        for (int wave = 1; wave <= 5; wave++) {
            s = game.step(s, List.of(new Command.StartWave(wave)), s.tick() + 1);
            while (s.wave().phase() == WaveState.Phase.RUNNING) {
                s = game.step(s, List.of(), s.tick() + 1);
            }
            if (right[wave] != null) {
                s = game.step(s, List.of(new Command.AnswerPostmortem(right[wave])), s.tick() + 1);
            }
        }
        return s;
    }

    @Test
    void serverMessagesMatchTheClientFixtures() throws IOException {
        for (Map.Entry<String, ServerMessage> e : serverMessages().entrySet()) {
            matchFixture(e.getKey(), json.writerFor(ServerMessage.class).writeValueAsString(e.getValue()));
        }
    }

    /** A sample of the catalog body: one plain counter, one gated by a counter, one with options, one action. */
    @Test
    void catalogMatchesTheClientFixture() throws IOException {
        CounterCatalog.Catalog full = new CounterCatalog(Game.chapterOne().rules())
                .visible(Language.EN, List.of("pg-stat-statements"));
        List<String> sample = List.of("prometheus", "grafana", "create-index");
        CounterCatalog.Catalog body = new CounterCatalog.Catalog(full.language(), full.crewPerShift(),
                full.counters().stream().filter(c -> sample.contains(c.id())).toList(),
                full.actions().stream().filter(a -> a.id().equals("clear-disk")).toList());

        assertThat(body.counters()).hasSize(3);
        matchFixture("catalog", json.writeValueAsString(body));
    }

    /** Two opened articles, in the order they were earned. */
    @Test
    void codexMatchesTheClientFixture() throws IOException {
        CodexBook.Codex body = new CodexBook(Game.chapterOne().rules().content())
                .opened(Language.EN, List.of("prometheus", "use-method", "no-such-entry"));

        assertThat(body.entries()).extracting(CodexBook.Article::id).containsExactly("prometheus", "use-method");
        matchFixture("codex", json.writeValueAsString(body));
    }

    private static void matchFixture(String name, String json) throws IOException {
        // The pretty printer uses the platform line separator; fixtures are LF everywhere.
        String expected = json.replace("\r\n", "\n") + "\n";
        Path dir = FIXTURES.resolve("server");
        Path file = dir.resolve(name + ".json");
        if (UPDATE) {
            Files.createDirectories(dir);
            Files.writeString(file, expected, StandardCharsets.UTF_8);
        }
        assertThat(file).as("fixture %s (regenerate with -Dcontract.update=true)", file).exists();
        assertThat(Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n")).as(name).isEqualTo(expected);
    }

    @Test
    void keyframeAndDeltaShowTheOptionalLists() {
        Map<String, ServerMessage> messages = serverMessages();
        ServerMessage.Snapshot keyframe = (ServerMessage.Snapshot) messages.get("snapshot-keyframe");
        ServerMessage.Snapshot delta = (ServerMessage.Snapshot) messages.get("snapshot-delta");

        assertThat(keyframe.counters()).extracting(Observation.Counter::id).containsExactly("prometheus", "grafana");
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
        expected.put("answer-postmortem", new ClientMessage.AnswerPostmortem("b"));
        expected.put("run-action", new ClientMessage.RunAction("clear-disk", "truncate"));

        for (Map.Entry<String, ClientMessage> e : expected.entrySet()) {
            Path file = FIXTURES.resolve("client").resolve(e.getKey() + ".json");
            JsonNode raw = json.readTree(Files.readString(file, StandardCharsets.UTF_8));
            assertThat(json.treeToValue(raw, ClientMessage.class)).as(e.getKey()).isEqualTo(e.getValue());
        }
    }
}
