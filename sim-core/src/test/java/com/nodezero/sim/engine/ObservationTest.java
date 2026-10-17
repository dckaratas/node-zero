package com.nodezero.sim.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nodezero.sim.engine.Command.ApplyCounter;
import com.nodezero.sim.engine.Command.StartWave;
import com.nodezero.sim.state.GameState;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Fog of war: what the server lets a player see (slice 3c, decisions D/H/I/J). */
class ObservationTest {

    private static Game game;

    @BeforeAll
    static void setUp() {
        // Practice: the festival keeps running, so complaints have time to appear.
        game = Game.chapterOne(GameBalance.PRACTICE);
    }

    private static GameState run(GameState s, long untilTick, List<Command> atNextTick) {
        long first = s.tick() + 1;
        for (long tick = first; tick <= untilTick; tick++) {
            s = game.step(s, tick == first ? atNextTick : List.of(), tick);
        }
        return s;
    }

    /** Buys the given modules at tick 1, lets them come online, then returns the state at tick 100. */
    private static GameState withModules(String... modules) {
        GameState s = game.newState(1);
        long tick = 0;
        for (String module : modules) {
            s = game.step(s, List.of(new ApplyCounter(module)), ++tick);
        }
        return run(s, 100, List.of());
    }

    @Test
    void countersShowWhenTheyFinishBuilding() {
        // prometheus: apply_time_s 5 = 50 ticks after it was applied at tick 1
        GameState s = game.step(game.newState(1), List.of(new ApplyCounter("prometheus")), 1);
        Observation building = game.observe(run(s, 20, List.of()));
        Observation ready = game.observe(run(s, 60, List.of()));

        assertThat(building.counters()).singleElement()
                .isEqualTo(new Observation.Counter("prometheus", null, 51));
        assertThat(building.counters().getFirst().isReady(building.tick())).isFalse();
        assertThat(building.metrics()).isEmpty();
        assertThat(ready.counters().getFirst().isReady(ready.tick())).isTrue();
        assertThat(ready.metrics()).isNotEmpty();
    }

    @Test
    void withoutMonitoringThePlayerSeesNoMetrics() {
        Observation view = game.observe(run(game.newState(1), 100, List.of(new StartWave(3))));

        assertThat(view.metrics()).isEmpty();
        assertThat(view.shield()).isBetween(0.0, 1.0); // the shield itself is always on the HUD
    }

    @Test
    void usersComplainOnlyAfterTheDelay() {
        GameState start = game.step(game.newState(1), List.of(new StartWave(3)), 1);
        GameState s = start;
        Long firstFailure = null;
        Long firstComplaint = null;
        for (long tick = 2; tick <= 900 && firstComplaint == null; tick++) {
            s = game.step(s, List.of(), tick);
            if (firstFailure == null && s.wave().symptomSinceTick() >= 0) {
                firstFailure = tick;
            }
            if (game.observe(s).signals().contains("user_complaints")) {
                firstComplaint = tick;
            }
        }

        assertThat(firstFailure).isNotNull();
        assertThat(firstComplaint).isNotNull();
        // traffic-surge: without_monitoring { signal: user_complaints, delay_s: 30 }
        assertThat((firstComplaint - firstFailure) * SimConstants.DT).isBetween(29.9, 30.2);
    }

    @Test
    void prometheusShowsRawMetricsButNotDatabaseOrPanels() {
        Observation view = game.observe(withModules("prometheus"));

        assertThat(view.metrics()).containsKeys("cpu_utilization", "request_rate", "hikaricp_connections_pending");
        assertThat(view.metrics()).doesNotContainKeys("db_cpu_utilization", "latency_avg", "slow_queries");
        assertThat(view.metrics().get("cpu_utilization")).isGreaterThan(0.4).isLessThan(0.6);
    }

    @Test
    void metricsAppearOnlyOnceTheModuleIsOnline() {
        // Prometheus takes 5 s to set up.
        GameState justBought = game.step(game.newState(1), List.of(new ApplyCounter("prometheus")), 1);
        GameState online = run(justBought, 51, List.of());

        assertThat(game.observe(justBought).metrics()).isEmpty();
        assertThat(game.observe(online).metrics()).isNotEmpty();
    }

    @Test
    void exporterAndGrafanaNeedPrometheusFirst() {
        GameState fresh = game.newState(1);

        assertThatThrownBy(() -> game.step(fresh, List.of(new ApplyCounter("postgres-exporter")), 1))
                .hasMessageContaining("needs prometheus installed first");
        assertThatThrownBy(() -> game.step(fresh, List.of(new ApplyCounter("grafana")), 1))
                .hasMessageContaining("needs prometheus installed first");
    }

    @Test
    void exporterRevealsTheDatabaseAndGrantsPgStatStatements() {
        GameState s = withModules("prometheus", "postgres-exporter");
        Observation view = game.observe(s);

        assertThat(view.metrics()).containsKeys("db_cpu_utilization", "db_io_utilization");
        assertThat(view.insights()).containsExactly("pg-stat-statements");
        // The insight is real: create-index can now be bought without any test-only command.
        GameState indexed = game.step(s, List.of(new ApplyCounter("create-index", "concurrently")), s.tick() + 1);
        assertThat(indexed.progress().isInstalled("create-index")).isTrue();
    }

    @Test
    void grafanaAddsPanelsAndTheTrafficHeatmapInsight() {
        Observation view = game.observe(withModules("prometheus", "grafana"));

        assertThat(view.metrics()).containsKeys("latency_avg", "latency_p99", "error_rate", "disk_forecast");
        assertThat(view.metrics()).doesNotContainKey("slow_queries"); // also needs the database exporter
        assertThat(view.insights()).containsExactly("traffic-heatmap");
        assertThat(view.metrics().get("latency_p99")).isGreaterThan(view.metrics().get("latency_avg"));
    }

    @Test
    void slowQueriesPanelNeedsGrafanaAndTheExporter() {
        Observation view = game.observe(withModules("prometheus", "postgres-exporter", "grafana"));

        assertThat(view.metrics()).containsKey("slow_queries");
    }

    @Test
    void metricsFollowCatalogOrder() {
        Observation view = game.observe(withModules("prometheus", "postgres-exporter", "grafana"));

        assertThat(view.metrics().keySet()).containsExactly(
                "request_rate", "cpu_utilization", "tomcat_threads_busy", "hikaricp_connections_active",
                "hikaricp_connections_pending", "disk_usage_ratio", "disk_fill_rate", "db_cpu_utilization",
                "db_io_utilization", "latency_avg", "latency_p99", "error_rate", "disk_forecast",
                "traffic_heatmap", "slow_queries");
    }

    @Test
    void theViewNeverLeaksTheCause() {
        GameState s = game.step(withModules("prometheus", "postgres-exporter", "grafana"),
                List.of(new StartWave(4)), 101);
        s = run(s, 800, List.of());
        String view = game.observe(s).toString();

        for (String threatId : game.rules().content().threats().keySet()) {
            assertThat(view).doesNotContain(threatId);
        }
        assertThat(view).doesNotContain("orders").doesNotContain("per_row");
    }
}
