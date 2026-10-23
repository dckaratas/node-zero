package com.nodezero.sim.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Broken-content scenarios against a minimal valid content set held in memory. */
class ContentLoaderValidationTest {

    private static final String THREAT = "threats/test-threat.yaml";
    private static final String COUNTERS = "counters/test.yaml";
    private static final String TR = "i18n/tr.yaml";
    private static final String CODEX = "codex/test.yaml";

    private final Map<String, String> files = validFiles();

    private Content load() {
        return ContentLoader.load(path -> files.containsKey(path)
                ? new ByteArrayInputStream(files.get(path).getBytes(StandardCharsets.UTF_8))
                : null);
    }

    private void edit(String file, String from, String to) {
        String before = files.get(file);
        assertThat(before).as("fixture edit target").contains(from);
        files.put(file, before.replace(from, to));
    }

    @Test
    void minimalContentIsValid() {
        Content content = load();

        assertThat(content.threats()).containsOnlyKeys("test-threat");
        assertThat(content.messages().text(Language.TR, "pm.test.q")).isEqualTo("Neden?");
    }

    @Test
    void unknownFieldIsReportedWithFileAndPath() {
        edit(THREAT, "  chapter: 1\n", "  chapter: 1\n  colour: red\n");

        // Jackson buffers record properties, so the line is where the enclosing object ends;
        // the field path is exact.
        assertThatThrownBy(this::load)
                .isInstanceOf(ContentException.class)
                .hasMessageMatching("(?s)" + THREAT + ":\\d+:\\d+ \\(threat\\.colour\\): unknown field 'colour'");
    }

    @Test
    void ruleViolationInsideRecordIsReportedWithFieldPath() {
        edit(THREAT, "{ id: b, key: pm.test.b }", "{ id: b, key: pm.test.b, correct: true }");

        assertThatThrownBy(this::load)
                .hasMessageContaining(THREAT + ":")
                .hasMessageContaining("threat.postmortem")
                .hasMessageContaining("exactly one postmortem option must be correct");
    }

    @Test
    void malformedConditionIsRejected() {
        edit(THREAT, "\"rho >= 0.9\"", "\"rho >> 0.9\"");

        assertThatThrownBy(this::load).hasMessageContaining("rho >> 0.9");
    }

    @Test
    void modelSpecificFieldIsRequired() {
        edit(THREAT, ", service_time_ms: 20", "");

        assertThatThrownBy(this::load).hasMessageContaining("service_time_ms is required");
    }

    @Test
    void lowercaseResourceNamesWorkUnderTurkishLocale() {
        // "disk" -> DISK must not become "DİSK" under tr_TR
        edit(THREAT, "resource: cpu, model: utilization, service_time_ms: 20", "resource: disk, model: fill, units_per_request: 0.002");

        assertThat(load().threat("test-threat").consumes().getFirst().resource().name()).isEqualTo("DISK");
    }

    @Test
    void crossFileProblemsAreReportedTogether() {
        edit(THREAT, "counters: [test-counter]", "counters: [test-counter, magic-fix]");
        edit(TR, "pm.test.b: \"B\"\n", "");

        assertThatThrownBy(this::load)
                .hasMessageContaining("unknown counter 'magic-fix'")
                .hasMessageContaining("missing TR text for 'pm.test.b'");
    }

    @Test
    void idMustMatchFileName() {
        edit(THREAT, "id: test-threat", "id: other-threat");

        assertThatThrownBy(this::load).hasMessageContaining("must match the file name 'test-threat'");
    }

    @Test
    void unusedTextKeysAreFlagged() {
        edit(TR, "tradeoff.test: \"T\"\n", "tradeoff.test: \"T\"\npm.orphan.key: \"X\"\n");

        assertThatThrownBy(this::load).hasMessageContaining("unused key 'pm.orphan.key'");
    }

    @Test
    void duplicateYamlKeysAreRejected() {
        edit(THREAT, "  chapter: 1\n", "  chapter: 1\n  chapter: 2\n");

        assertThatThrownBy(this::load).isInstanceOf(ContentException.class).hasMessageContaining("chapter");
    }

    @Test
    void unknownCodexReferenceIsReported() {
        edit(THREAT, "codex: [utilization-law]", "codex: [utilization-law, magic-law]");

        assertThatThrownBy(this::load).hasMessageContaining("threat test-threat: unknown codex entry 'magic-law'");
    }

    @Test
    void codexEntryNothingLinksToIsReported() {
        edit(CODEX, "codex:\n", "codex:\n  - { id: orphan, title: { en: O, tr: O }, body: { en: O, tr: O } }\n");

        assertThatThrownBy(this::load).hasMessageContaining("codex orphan: not referenced by any threat, counter or action");
    }

    @Test
    void missingFileIsReported() {
        files.remove(COUNTERS);

        assertThatThrownBy(this::load).hasMessageContaining(COUNTERS + ": file not found");
    }

    private static Map<String, String> validFiles() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("index.yaml", """
                threats: [threats/test-threat.yaml]
                counters: [counters/test.yaml]
                codex: [codex/test.yaml]
                messages: { en: i18n/en.yaml, tr: i18n/tr.yaml }
                """);
        files.put(THREAT, """
                threat:
                  id: test-threat
                  name: { en: "Test", tr: "Deneme" }
                  chapter: 1
                  trigger: { wave: 1, event: deploy, briefing_key: briefing.test }
                  traffic:
                    baseline_rps: 100
                    shape:
                      - { phase: plateau, duration_s: 10, multiplier: 1.0 }
                    mix: { read: 0.5, write: 0.5 }
                  consumes:
                    - { resource: cpu, model: utilization, service_time_ms: 20 }
                  effects:
                    - { when: "rho >= 0.9", effect: slo_breach_risk, visual: reactor_glow_red }
                  visibility:
                    without_monitoring: { signal: user_complaints, delay_s: 30 }
                    prometheus: [cpu_utilization]
                    grafana: [latency_avg]
                  counters: [test-counter]
                  postmortem:
                    question_key: pm.test.q
                    options:
                      - { id: a, key: pm.test.a, correct: true }
                      - { id: b, key: pm.test.b }
                    reward: { unlock: test-panel }
                  codex: [utilization-law]
                """);
        files.put(COUNTERS, """
                counters:
                  - id: test-counter
                    name: { en: "Fix", tr: "Düzelt" }
                    real_tech: "Nginx limit_req"
                    cost: { credits: 100, upkeep_per_wave: 0 }
                    toil: 0
                    apply_time_s: 1
                    effect: { type: rate_limit, target: cpu, value: 150 }
                    side_effect:
                      - { type: reject_with_status, value: 429 }
                    tradeoff_key: tradeoff.test
                """);
        files.put(CODEX, """
                codex:
                  - id: utilization-law
                    title: { en: "Utilization law", tr: "Kullanım yasası" }
                    body: { en: "U = X S", tr: "U = X S" }
                """);
        files.put("i18n/en.yaml", """
                pm.test.q: "Why?"
                briefing.test: "Deploy"
                briefing.quiet: "Quiet"
                pm.test.a: "A"
                pm.test.b: "B"
                tradeoff.test: "T"
                """);
        files.put(TR, """
                pm.test.q: "Neden?"
                briefing.test: "Deploy"
                briefing.quiet: "Sakin"
                pm.test.a: "A"
                pm.test.b: "B"
                tradeoff.test: "T"
                """);
        return files;
    }
}
