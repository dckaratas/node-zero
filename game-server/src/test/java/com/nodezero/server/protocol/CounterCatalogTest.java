package com.nodezero.server.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import com.nodezero.sim.content.Language;
import com.nodezero.sim.engine.Game;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CounterCatalogTest {

    private static CounterCatalog catalog;

    @BeforeAll
    static void setUp() {
        catalog = new CounterCatalog(Game.chapterOne().rules());
    }

    private static List<String> ids(CounterCatalog.Catalog c) {
        return c.counters().stream().map(CounterCatalog.Item::id).toList();
    }

    @Test
    void countersThatNeedAnInsightStayHiddenUntilThePlayerHasIt() {
        List<String> before = ids(catalog.visible(Language.EN, List.of()));
        List<String> after = ids(catalog.visible(Language.EN, List.of("pg-stat-statements")));

        assertThat(before).contains("prometheus", "statement-timeout").doesNotContain("create-index", "reschedule-job");
        assertThat(after).contains("create-index").doesNotContain("reschedule-job");
        assertThat(after).hasSize(before.size() + 1);
    }

    @Test
    void countersGatedByAnotherCounterAreListedWithTheirRequirement() {
        CounterCatalog.Item grafana = catalog.visible(Language.EN, List.of()).counters().stream()
                .filter(c -> c.id().equals("grafana")).findFirst().orElseThrow();

        assertThat(grafana.requiresCounters()).containsExactly("prometheus");
        assertThat(grafana.grantsInsights()).containsExactly("traffic-heatmap");
    }

    @Test
    void textsComeInTheRequestedLanguage() {
        CounterCatalog.Item en = catalog.visible(Language.EN, List.of()).counters().getFirst();
        CounterCatalog.Item tr = catalog.visible(Language.TR, List.of()).counters().getFirst();

        assertThat(en.id()).isEqualTo(tr.id()).isEqualTo("prometheus");
        assertThat(en.name()).isEqualTo("Sensor Array");
        assertThat(tr.name()).isEqualTo("Sensör Dizisi");
        assertThat(en.tradeoff()).startsWith("Lifts the fog");
    }
}
