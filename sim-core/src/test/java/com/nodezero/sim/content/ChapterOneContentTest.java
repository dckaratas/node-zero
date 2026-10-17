package com.nodezero.sim.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.nodezero.sim.content.ThreatDef.PhaseKind;
import com.nodezero.sim.state.ResourceId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The shipped Chapter 1 content loads, validates and matches the GDD. */
class ChapterOneContentTest {

    private static Content content;

    @BeforeAll
    static void load() {
        content = ContentLoader.loadDefault();
    }

    @Test
    void loadsAllChapterOneThreatsInPlayOrder() {
        assertThat(content.threats().keySet())
                .containsExactly("log-storm", "traffic-surge", "slow-endpoint", "nightly-report-job");
        assertThat(content.threats().values()).allMatch(t -> t.chapter() == 1);
        assertThat(content.counters()).hasSize(12);
    }

    @Test
    void eachThreatUsesTheModelAgreedInTheGdd() {
        assertThat(content.threat("log-storm").consumes().getFirst().model()).isEqualTo(ModelKind.FILL);
        assertThat(content.threat("traffic-surge").consumes().getFirst().model()).isEqualTo(ModelKind.UTILIZATION);
        // Only the root cause is declared; pool and thread saturation emerge from the pipeline.
        assertThat(content.threat("slow-endpoint").consumes())
                .extracting(ThreatDef.Consumption::resource)
                .containsExactly(ResourceId.DB_CPU);
        assertThat(content.threat("nightly-report-job").consumes())
                .extracting(ThreatDef.Consumption::model)
                .containsExactly(ModelKind.UTILIZATION, ModelKind.OCCUPANCY);
    }

    @Test
    void trafficSurgeMatchesTheGdd() {
        ThreatDef surge = content.threat("traffic-surge");

        assertThat(surge.trigger().wave()).isEqualTo(3);
        assertThat(surge.trigger().warning().wave()).isEqualTo(2);
        assertThat(surge.traffic().shape())
                .extracting(ThreatDef.Phase::phase)
                .containsExactly(PhaseKind.RAMP, PhaseKind.PLATEAU, PhaseKind.DECAY);
        assertThat(surge.traffic().shape().get(0).multiplier()).isEqualTo(new Multiplier(1.0, 3.0));
        assertThat(surge.effects().getFirst().when()).hasToString("rho >= 0.7");
        assertThat(surge.lockedCounters().getFirst().unlocksInChapter()).isEqualTo(3);
    }

    @Test
    void ddosIsTheDecoyNotTheAnswer() {
        ThreatDef.Postmortem pm = content.threat("traffic-surge").postmortem();

        assertThat(pm.options()).filteredOn(ThreatDef.Option::correct)
                .extracting(ThreatDef.Option::key)
                .containsExactly("pm.traffic_surge.capacity_not_planned");
    }

    @Test
    void naiveRetryIsATrapThatPointsToABetterAlternative() {
        CounterDef retry = content.counter("naive-retry");

        assertThat(retry.sideEffect()).extracting(CounterDef.EffectSpec::type).contains("load_multiplier");
        assertThat(retry.betterAlternative()).isEqualTo("bulkhead-pool");
    }

    @Test
    void createIndexOffersBlockingAndConcurrentOptions() {
        CounterDef index = content.counter("create-index");

        assertThat(index.requiresInsight()).isEqualTo("pg-stat-statements");
        assertThat(index.options()).extracting(CounterDef.CounterOption::id).containsExactly("blocking", "concurrently");
    }

    @Test
    void everyTextExistsInBothLanguages() {
        assertThat(content.messages().en().keySet()).isEqualTo(content.messages().tr().keySet());
        assertThat(content.messages().text(Language.TR, "pm.traffic_surge.ddos")).isEqualTo("DDoS saldırısı");
    }
}
