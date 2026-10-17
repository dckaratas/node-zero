package com.nodezero.sim.content;

import com.nodezero.sim.state.ResourceId;
import java.util.ArrayList;
import java.util.List;

/**
 * A counter-measure as written in {@code content/counters/*.yaml}. No counter is free: every one
 * has a cost and usually a side effect (GDD §3). Effects are declarative; Slice 3 interprets them.
 */
public record CounterDef(
        String id,
        LocalizedText name,
        String realTech,
        Cost cost,
        int toil,
        double applyTimeS,
        List<ConfigParam> config,
        List<CounterOption> options,
        String requiresInsight,
        List<String> requiresCounters,
        List<String> grantsInsights,
        EffectSpec effect,
        List<EffectSpec> sideEffect,
        String tradeoffKey,
        String betterAlternative,
        List<String> codex) {

    public CounterDef {
        Require.id(id, "id");
        Require.present(name, "name");
        Require.nonBlank(realTech, "real_tech");
        Require.present(cost, "cost");
        Require.atLeast(toil, 0, "toil");
        Require.nonNegative(applyTimeS, "apply_time_s");
        config = Require.orEmpty(config);
        options = Require.orEmpty(options);
        if (requiresInsight != null) {
            Require.id(requiresInsight, "requires_insight");
        }
        requiresCounters = Require.orEmpty(requiresCounters);
        requiresCounters.forEach(c -> Require.id(c, "requires_counters[]"));
        grantsInsights = Require.orEmpty(grantsInsights);
        grantsInsights.forEach(i -> Require.id(i, "grants_insights[]"));
        Require.present(effect, "effect");
        sideEffect = Require.orEmpty(sideEffect);
        Require.textKey(tradeoffKey, "tradeoff_key");
        if (betterAlternative != null) {
            Require.id(betterAlternative, "better_alternative");
        }
        codex = Require.orEmpty(codex);
        codex.forEach(c -> Require.id(c, "codex[]"));
    }

    public List<String> textKeys() {
        List<String> keys = new ArrayList<>();
        keys.add(tradeoffKey);
        options.forEach(o -> keys.add(o.tradeoffKey()));
        return List.copyOf(keys);
    }

    public record Cost(int credits, int upkeepPerWave) {
        public Cost {
            Require.atLeast(credits, 0, "cost.credits");
            Require.atLeast(upkeepPerWave, 0, "cost.upkeep_per_wave");
        }
    }

    /** A tunable parameter; {@code playerEditable} ones show up in the module's config panel. */
    public record ConfigParam(String key, double value, Double min, Double max, boolean playerEditable) {
        public ConfigParam {
            Require.snake(key, "config[].key");
            if ((min != null && value < min) || (max != null && value > max)) {
                throw new IllegalArgumentException("config " + key + " value must be within [min, max]");
            }
        }
    }

    /** Alternative way to apply the same counter, e.g. CREATE INDEX vs CREATE INDEX CONCURRENTLY. */
    public record CounterOption(
            String id, LocalizedText name, Double applyTimeS, List<EffectSpec> sideEffect, String tradeoffKey) {
        public CounterOption {
            Require.id(id, "options[].id");
            Require.present(name, "options[].name");
            if (applyTimeS != null) {
                Require.nonNegative(applyTimeS, "options[].apply_time_s");
            }
            sideEffect = Require.orEmpty(sideEffect);
            Require.textKey(tradeoffKey, "options[].tradeoff_key");
        }
    }

    /** Declarative effect, e.g. {@code { type: add_servers, target: cpu, value: 4 }}. */
    public record EffectSpec(String type, ResourceId target, Double value) {
        public EffectSpec {
            Require.snake(type, "type");
        }
    }
}
