package com.nodezero.sim.engine;

import com.nodezero.sim.content.Content;
import com.nodezero.sim.content.CounterDef;
import com.nodezero.sim.content.ModelKind;
import com.nodezero.sim.content.ThreatDef;
import com.nodezero.sim.model.FillModel;
import com.nodezero.sim.model.OccupancyModel;
import com.nodezero.sim.model.ResourceModel;
import com.nodezero.sim.model.UtilizationModel;
import com.nodezero.sim.state.ResourceId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything static a game needs: station hardware, validated content and balance numbers.
 * Checked and pre-resolved once at construction so the per-tick path never parses or validates.
 */
public final class Rules {

    /** Resources the request pipeline reads and the model kind it expects for each. */
    private static final List<Required> PIPELINE = List.of(
            new Required(ResourceId.CPU, ModelKind.UTILIZATION),
            new Required(ResourceId.THREADS, ModelKind.OCCUPANCY),
            new Required(ResourceId.DB_POOL, ModelKind.OCCUPANCY),
            new Required(ResourceId.DISK, ModelKind.FILL),
            new Required(ResourceId.DB_CPU, ModelKind.UTILIZATION),
            new Required(ResourceId.DB_IO, ModelKind.UTILIZATION));

    private final StationLayout station;
    private final Content content;
    private final GameBalance balance;
    private final ThreatDef[] threatByWave;
    private final Map<String, CounterRules> counters;

    public Rules(StationLayout station, Content content) {
        this(station, content, GameBalance.DEFAULT);
    }

    public Rules(StationLayout station, Content content, GameBalance balance) {
        this.station = station;
        this.content = content;
        this.balance = balance;
        List<String> problems = new ArrayList<>();
        checkStation(problems);
        checkThreats(problems);
        this.counters = resolveCounters(problems);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("invalid rules:\n  - " + String.join("\n  - ", problems));
        }
        this.threatByWave = indexByWave();
    }

    public StationLayout station() {
        return station;
    }

    public Content content() {
        return content;
    }

    public GameBalance balance() {
        return balance;
    }

    /** The threat a wave brings, or {@code null} for a quiet wave. */
    public ThreatDef threatForWave(int wave) {
        return wave > 0 && wave < threatByWave.length ? threatByWave[wave] : null;
    }

    CounterRules counter(String id) {
        CounterRules rules = counters.get(id);
        if (rules == null) {
            throw new IllegalArgumentException("unknown counter: " + id);
        }
        return rules;
    }

    static ModelKind kindOf(ResourceModel<?, ?> model) {
        return switch (model) {
            case UtilizationModel m -> ModelKind.UTILIZATION;
            case OccupancyModel m -> ModelKind.OCCUPANCY;
            case FillModel m -> ModelKind.FILL;
        };
    }

    private void checkStation(List<String> problems) {
        for (Required r : PIPELINE) {
            ResourceModel<?, ?> model = station.models().get(r.resource());
            if (model == null || kindOf(model) != r.kind()) {
                problems.add("station needs " + r.resource() + " as a " + r.kind() + " model");
            }
        }
    }

    private void checkThreats(List<String> problems) {
        for (ThreatDef threat : content.threats().values()) {
            checkMetrics(threat, threat.visibility().prometheus(), false, problems);
            checkMetrics(threat, threat.visibility().grafana(), true, problems);
            for (ThreatDef.Consumption c : threat.consumes()) {
                ResourceModel<?, ?> model = station.models().get(c.resource());
                if (model != null && kindOf(model) != c.model()) {
                    problems.add("threat " + threat.id() + ": " + c.resource() + " is "
                            + kindOf(model) + " in the station, not " + c.model());
                }
            }
        }
    }

    /** Content may only name metrics the catalog has, under the right section (raw vs. panel). */
    private static void checkMetrics(ThreatDef threat, List<String> names, boolean panels, List<String> problems) {
        for (String name : names) {
            Metric metric = Metric.byName(name);
            if (metric == null) {
                problems.add("threat " + threat.id() + ": unknown metric '" + name + "'");
            } else if (metric.tier().isPanel() != panels) {
                problems.add("threat " + threat.id() + ": '" + name + "' belongs under "
                        + (metric.tier().isPanel() ? "grafana" : "prometheus"));
            }
        }
    }

    private Map<String, CounterRules> resolveCounters(List<String> problems) {
        Map<String, CounterRules> resolved = new LinkedHashMap<>();
        for (CounterDef def : content.counters().values()) {
            List<Effect> base = new ArrayList<>();
            addEffect(def.id(), def.effect(), base, problems);
            def.sideEffect().forEach(e -> addEffect(def.id(), e, base, problems));

            Map<String, CounterRules.Variant> variants = new LinkedHashMap<>();
            if (def.options().isEmpty()) {
                variants.put("", new CounterRules.Variant(def.applyTimeS(), List.copyOf(base)));
            }
            for (CounterDef.CounterOption option : def.options()) {
                List<Effect> effects = new ArrayList<>(base);
                option.sideEffect().forEach(e -> addEffect(def.id(), e, effects, problems));
                double applyTime = option.applyTimeS() != null ? option.applyTimeS() : def.applyTimeS();
                variants.put(option.id(), new CounterRules.Variant(applyTime, List.copyOf(effects)));
            }
            resolved.put(def.id(), new CounterRules(def, Collections.unmodifiableMap(variants)));
        }
        return Collections.unmodifiableMap(resolved);
    }

    private void addEffect(String counterId, CounterDef.EffectSpec spec, List<Effect> into, List<String> problems) {
        EffectKind kind;
        try {
            // Locale.ROOT: under tr_TR "write_lock".toUpperCase() would yield "WRİTE_LOCK".
            kind = EffectKind.valueOf(spec.type().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            problems.add("counter " + counterId + ": unknown effect type '" + spec.type() + "'");
            return;
        }
        if (kind.targetKind != null) {
            ResourceModel<?, ?> model = spec.target() == null ? null : station.models().get(spec.target());
            if (model == null || kindOf(model) != kind.targetKind) {
                problems.add("counter " + counterId + ": " + spec.type() + " needs a " + kind.targetKind
                        + " target, got " + spec.target());
                return;
            }
        }
        if (kind.needsValue && spec.value() == null) {
            problems.add("counter " + counterId + ": " + spec.type() + " needs a value");
            return;
        }
        into.add(new Effect(kind, spec.target(), spec.value() == null ? 0 : spec.value()));
    }

    private ThreatDef[] indexByWave() {
        int maxWave = 0;
        for (ThreatDef t : content.threats().values()) {
            maxWave = Math.max(maxWave, t.trigger().wave());
        }
        ThreatDef[] byWave = new ThreatDef[maxWave + 1];
        for (ThreatDef t : content.threats().values()) {
            byWave[t.trigger().wave()] = t;
        }
        return byWave;
    }

    /** One resolved effect of a counter. */
    record Effect(EffectKind kind, ResourceId target, double value) {}

    /** A counter with its effects resolved per option ({@code ""} when it has no options). */
    record CounterRules(CounterDef def, Map<String, Variant> variants) {

        record Variant(double applyTimeS, List<Effect> effects) {}

        Variant variant(String option) {
            Variant v = variants.get(option == null ? "" : option);
            if (v == null) {
                throw new IllegalArgumentException("counter " + def.id() + " has no option '" + option
                        + "'; choose one of " + variants.keySet());
            }
            return v;
        }
    }

    private record Required(ResourceId resource, ModelKind kind) {}
}
