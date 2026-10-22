package com.nodezero.sim.engine;

import com.nodezero.sim.content.ActionDef;
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
    private final int chapter;
    private final Map<String, CounterRules> counters;
    private final Map<String, ActionRules> actions;

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
        this.actions = resolveActions(problems);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("invalid rules:\n  - " + String.join("\n  - ", problems));
        }
        this.threatByWave = indexByWave();
        this.chapter = content.threats().values().stream().mapToInt(ThreatDef::chapter).findFirst().orElse(1);
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

    /** The last wave of the chapter: the highest wave a threat arrives in. */
    public int lastWave() {
        return threatByWave.length - 1;
    }

    /** The chapter being played, from its threats; one chapter per game until chapter 2 brings a campaign. */
    public int chapter() {
        return chapter;
    }

    /** The threat a wave brings, or {@code null} for a quiet wave. */
    public ThreatDef threatForWave(int wave) {
        return wave > 0 && wave < threatByWave.length ? threatByWave[wave] : null;
    }

    ActionRules action(String id) {
        ActionRules rules = actions.get(id);
        if (rules == null) {
            throw new IllegalArgumentException("unknown action: " + id);
        }
        return rules;
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
            String owner = "counter " + def.id();
            addCounterEffect(owner, def.effect(), base, problems);
            def.sideEffect().forEach(e -> addCounterEffect(owner, e, base, problems));

            Map<String, CounterRules.Variant> variants = new LinkedHashMap<>();
            if (def.options().isEmpty()) {
                variants.put("", new CounterRules.Variant(def.applyTimeS(), List.copyOf(base)));
            }
            for (CounterDef.CounterOption option : def.options()) {
                List<Effect> effects = new ArrayList<>(base);
                option.sideEffect().forEach(e -> addCounterEffect(owner, e, effects, problems));
                double applyTime = option.applyTimeS() != null ? option.applyTimeS() : def.applyTimeS();
                variants.put(option.id(), new CounterRules.Variant(applyTime, List.copyOf(effects)));
            }
            resolved.put(def.id(), new CounterRules(def, Collections.unmodifiableMap(variants)));
        }
        return Collections.unmodifiableMap(resolved);
    }

    private void addCounterEffect(String owner, CounterDef.EffectSpec spec, List<Effect> into, List<String> problems) {
        Effect effect = effect(owner, spec.type(), spec.target(), spec.value(), problems);
        if (effect != null && effect.kind().timing == EffectKind.Timing.INSTANT) {
            problems.add(owner + ": " + spec.type() + " is an incident action effect");
        } else if (effect != null) {
            into.add(effect);
        }
    }

    private Map<String, ActionRules> resolveActions(List<String> problems) {
        Map<String, ActionRules> resolved = new LinkedHashMap<>();
        for (ActionDef def : content.actions().values()) {
            String owner = "action " + def.id();
            List<Step> base = new ArrayList<>();
            def.effects().forEach(e -> addStep(owner, e, base, problems));
            Map<String, List<Step>> variants = new LinkedHashMap<>();
            if (def.options().isEmpty()) {
                variants.put("", List.copyOf(base));
            }
            for (ActionDef.ActionOption option : def.options()) {
                List<Step> steps = new ArrayList<>(base);
                option.effects().forEach(e -> addStep(owner, e, steps, problems));
                variants.put(option.id(), List.copyOf(steps));
            }
            resolved.put(def.id(), new ActionRules(def, Collections.unmodifiableMap(variants),
                    Math.round(def.cooldownS() / SimConstants.DT)));
        }
        return Collections.unmodifiableMap(resolved);
    }

    /** Instant effects happen when the action runs; build-time effects (downtime, cold JVM) need a window. */
    private void addStep(String owner, ActionDef.ActionEffect spec, List<Step> into, List<String> problems) {
        Effect effect = effect(owner, spec.type(), spec.target(), spec.value(), problems);
        if (effect == null) {
            return;
        }
        EffectKind.Timing timing = effect.kind().timing;
        if (spec.instant() && timing != EffectKind.Timing.INSTANT && timing != EffectKind.Timing.NARRATIVE) {
            problems.add(owner + ": " + spec.type() + " needs from_s and to_s");
        } else if (!spec.instant() && timing != EffectKind.Timing.BUILD) {
            problems.add(owner + ": " + spec.type() + " cannot have a window");
        } else {
            long from = spec.instant() ? 0 : Math.round(spec.fromS() / SimConstants.DT);
            long to = spec.instant() ? 0 : Math.round(spec.toS() / SimConstants.DT);
            into.add(new Step(effect, spec.instant(), from, to));
        }
    }

    private Effect effect(String owner, String type, ResourceId target, Double value, List<String> problems) {
        EffectKind kind;
        try {
            // Locale.ROOT: under tr_TR "write_lock".toUpperCase() would yield "WRİTE_LOCK".
            kind = EffectKind.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            problems.add(owner + ": unknown effect type '" + type + "'");
            return null;
        }
        if (kind.targetKind != null) {
            ResourceModel<?, ?> model = target == null ? null : station.models().get(target);
            if (model == null || kindOf(model) != kind.targetKind) {
                problems.add(owner + ": " + type + " needs a " + kind.targetKind + " target, got " + target);
                return null;
            }
        }
        if (kind.needsValue && value == null) {
            problems.add(owner + ": " + type + " needs a value");
            return null;
        }
        return new Effect(kind, target, value == null ? 0 : value);
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

    /**
     * An action effect: instant ones apply when the action runs; the others hold for ticks
     * {@code [start + fromTick, start + toTick)}.
     */
    record Step(Effect effect, boolean instant, long fromTick, long toTick) {}

    /** An action with its steps resolved per option ({@code ""} when it has none) and its cooldown in ticks. */
    record ActionRules(ActionDef def, Map<String, List<Step>> variants, long cooldownTicks) {

        List<Step> steps(String option) {
            List<Step> steps = variants.get(option == null ? "" : option);
            if (steps == null) {
                throw new IllegalArgumentException("action " + def.id() + " has no option '" + option
                        + "'; choose one of " + variants.keySet());
            }
            return steps;
        }

        /** Ticks after the start until the last windowed effect ends (0 for an instant action). */
        long durationTicks(String option) {
            long end = 0;
            for (Step step : steps(option)) {
                end = Math.max(end, step.toTick());
            }
            return end;
        }
    }

    /** A counter with its effects resolved per option ({@code ""} when it has no options). */
    record CounterRules(CounterDef def, Map<String, Variant> variants) {

        /** {@code effects} starts with the main effect, then the side effects (a setting replaces the first value). */
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
