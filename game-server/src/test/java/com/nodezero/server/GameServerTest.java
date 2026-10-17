package com.nodezero.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The real server on a random port, driven by a real WebSocket client. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GameServerTest {

    @Value("${local.server.port}")
    int port;

    private final JsonMapper json = JsonMapper.builder().build();
    private final BlockingQueue<JsonNode> inbox = new LinkedBlockingQueue<>();
    private WebSocketSession socket;

    private void connect() throws Exception {
        socket = new StandardWebSocketClient()
                .execute(new TextWebSocketHandler() {
                    @Override
                    protected void handleTextMessage(WebSocketSession s, TextMessage message) {
                        inbox.add(json.readTree(message.getPayload()));
                    }
                }, "ws://localhost:" + port + "/ws/game")
                .get(5, TimeUnit.SECONDS);
    }

    private JsonNode await(Predicate<JsonNode> condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            JsonNode message = inbox.poll(100, TimeUnit.MILLISECONDS);
            if (message != null && condition.test(message)) {
                return message;
            }
        }
        throw new AssertionError("no matching message within 5 s");
    }

    private static Predicate<JsonNode> type(String type) {
        return m -> type.equals(m.path("type").asString());
    }

    private void send(String message) throws Exception {
        socket.sendMessage(new TextMessage(message));
    }

    @AfterEach
    void close() throws Exception {
        if (socket != null && socket.isOpen()) {
            socket.close();
        }
    }

    @Test
    void connectionGetsWelcomeThenSnapshotsAtTenHertz() throws Exception {
        connect();

        JsonNode welcome = await(type("welcome"));
        assertThat(welcome.path("tickHz").asInt()).isEqualTo(10);

        long first = await(type("snapshot")).path("observation").path("tick").asLong();
        Thread.sleep(1000);
        inbox.clear(); // the queue holds a second's worth of snapshots; read a fresh one
        long later = await(type("snapshot")).path("observation").path("tick").asLong();
        assertThat(later - first).isBetween(5L, 20L);
    }

    @Test
    void startWaveShowsUpInTheNextSnapshots() throws Exception {
        connect();
        await(type("welcome"));

        send("{\"type\":\"start_wave\",\"wave\":1}");

        JsonNode snapshot = await(m -> type("snapshot").test(m) && m.path("observation").path("wave").asInt() == 1);
        assertThat(snapshot.path("observation").path("phase").asString()).isEqualTo("RUNNING");
    }

    @Test
    void ruleViolationsComeBackAsRejected() throws Exception {
        connect();
        await(type("welcome"));

        send("{\"type\":\"apply_counter\",\"counter\":\"grafana\"}");

        JsonNode rejected = await(type("rejected"));
        assertThat(rejected.path("reason").asString()).contains("needs prometheus installed first");
        assertThat(rejected.path("action").path("counter").asString()).isEqualTo("grafana");
    }

    @Test
    void garbageGetsAnErrorNotADisconnect() throws Exception {
        connect();
        await(type("welcome"));

        send("not json");

        assertThat(await(type("error")).path("reason").asString()).contains("cannot read message");
        assertThat(socket.isOpen()).isTrue();
    }

    @Test
    void replayLogAndMetricsAreServedOverHttp() throws Exception {
        connect();
        String session = await(type("welcome")).path("session").asString();
        send("{\"type\":\"apply_counter\",\"counter\":\"prometheus\"}");
        await(m -> type("snapshot").test(m)
                && m.path("observation").path("counters").toString().contains("prometheus"));

        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> replay = http.send(HttpRequest.newBuilder(URI.create(
                "http://localhost:" + port + "/api/sessions/" + session + "/replay")).build(),
                HttpResponse.BodyHandlers.ofString());
        String metrics = http.send(HttpRequest.newBuilder(URI.create(
                "http://localhost:" + port + "/actuator/prometheus")).build(),
                HttpResponse.BodyHandlers.ofString()).body();

        assertThat(replay.statusCode()).isEqualTo(200);
        assertThat(json.readTree(replay.body()).path("actions").get(0).path("action").path("counter").asString())
                .isEqualTo("prometheus");
        assertThat(metrics).contains("nodezero_tick_loop_seconds").contains("nodezero_sessions");
    }
}
