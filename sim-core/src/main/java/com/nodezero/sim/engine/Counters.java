package com.nodezero.sim.engine;

import com.nodezero.sim.content.CounterDef;
import com.nodezero.sim.model.FillModel;
import com.nodezero.sim.model.FillState;
import com.nodezero.sim.model.FlowLoad;
import com.nodezero.sim.model.HoldLoad;
import com.nodezero.sim.model.OccupancyModel;
import com.nodezero.sim.model.OccupancyState;
import com.nodezero.sim.model.RateLoad;
import com.nodezero.sim.model.UtilizationModel;
import com.nodezero.sim.model.UtilizationState;
import com.nodezero.sim.state.GameState;
import com.nodezero.sim.state.InstalledCounter;
import com.nodezero.sim.state.Progress;
import com.nodezero.sim.state.ResourceId;
import com.nodezero.sim.state.ResourceNode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Buying counters and applying their one-off hardware changes when they become active. */
final class Counters {

    /** Crew a config change takes (slice 7c). */
    static final int CONFIGURE_CREW = 1;

    private final Rules rules;

    Counters(Rules rules) {
        this.rules = rules;
    }

    GameState apply(GameState s, Command.ApplyCounter command, long tick) {
        Rules.CounterRules counter = rules.counter(command.counterId());
        CounterDef def = counter.def();
        Progress p = s.progress();
        if (p.isInstalled(def.id())) {
            throw new IllegalArgumentException("counter " + def.id() + " is already installed");
        }
        for (String required : def.requiresCounters()) {
            if (!p.isInstalled(required)) {
                throw new IllegalArgumentException("counter " + def.id() + " needs " + required + " installed first");
            }
        }
        if (def.requiresInsight() != null && !p.insights().contains(def.requiresInsight())) {
            throw new IllegalArgumentException("counter " + def.id() + " needs insight " + def.requiresInsight());
        }
        // Toil comes out of the same crew as incident actions (decision AG).
        requireCrew(p, def);
        Rules.CounterRules.Variant variant = counter.variant(command.optionId());
        int cost = variant.credits();
        if (p.credits() < cost) {
            throw new IllegalArgumentException(
                    "counter " + def.id() + " costs " + cost + " credits, only " + p.credits() + " left");
        }
        long activeTick = tick + Math.round(variant.applyTimeS() / SimConstants.DT);
        InstalledCounter installed = new InstalledCounter(def.id(), command.optionId(), tick, activeTick);
        // Buying a counter opens its codex entries (decision AB).
        return s.withProgress(p.withCredits(p.credits() - cost).withCounter(installed).withCodex(def.codex())
                .withOps(p.ops().withCrew(p.ops().crew() - def.toil())));
    }

    /**
     * Removes an installed counter (slice 7c, decision AI). Undoing takes as long as building did and costs the
     * same crew; the counter keeps working until then. No refund: the credits are spent. What other counters
     * build on must stay, and some changes cannot be undone at all.
     */
    GameState remove(GameState s, Command.RemoveCounter command, long tick) {
        Progress p = s.progress();
        InstalledCounter installed = installed(p, command.counterId());
        CounterDef def = rules.counter(installed.id()).def();
        if (!def.removable()) {
            throw new IllegalArgumentException("counter " + def.id() + " cannot be undone");
        }
        if (installed.isRemoving()) {
            throw new IllegalArgumentException("counter " + def.id() + " is already being removed");
        }
        if (!installed.isActive(tick)) {
            throw new IllegalArgumentException("counter " + def.id() + " is still being built");
        }
        for (InstalledCounter other : p.counters()) {
            if (rules.counter(other.id()).def().requiresCounters().contains(def.id())) {
                throw new IllegalArgumentException(other.id() + " needs " + def.id() + "; remove it first");
            }
        }
        requireCrew(p, def);
        double seconds = rules.counter(def.id()).variant(installed.option()).applyTimeS();
        long done = tick + Math.max(1, Math.round(seconds / SimConstants.DT));
        return s.withProgress(p.withCounters(replace(p, installed.removedAt(done)))
                .withOps(p.ops().withCrew(p.ops().crew() - def.toil())));
    }

    /**
     * Sets the value of the setting that drives a counter's effect (slice 7c), e.g. Nginx {@code rate_rps}.
     * A config reload is quick but still hands-on: one crew point, effective from this tick.
     */
    GameState configure(GameState s, Command.ConfigureCounter command, long tick) {
        Progress p = s.progress();
        InstalledCounter installed = installed(p, command.counterId());
        CounterDef.ConfigParam setting = rules.counter(installed.id()).def().setting();
        if (setting == null) {
            throw new IllegalArgumentException("counter " + installed.id() + " has no setting to change");
        }
        if (command.value() < setting.min() || command.value() > setting.max()) {
            throw new IllegalArgumentException(setting.key() + " must be within [" + setting.min() + ", "
                    + setting.max() + "]: " + command.value());
        }
        if (!installed.isActive(tick) || installed.isRemoving()) {
            throw new IllegalArgumentException("counter " + installed.id() + " is not running");
        }
        if (p.ops().crew() < CONFIGURE_CREW) {
            throw new IllegalArgumentException("changing " + setting.key() + " needs " + CONFIGURE_CREW
                    + " crew, only " + p.ops().crew() + " left");
        }
        return s.withProgress(p.withCounters(replace(p, installed.withSetting(command.value())))
                .withOps(p.ops().withCrew(p.ops().crew() - CONFIGURE_CREW)));
    }

    /**
     * Counters that become active this tick grant their insights (monitoring reveals what it measures) and
     * apply structural effects (more cores, threads, disk); counters whose removal completes are dropped and
     * their structural effects undone. Insights stay: what was learned is not forgotten.
     */
    GameState activate(GameState s, long tick) {
        EnumMap<ResourceId, ResourceNode<?, ?>> nodes = null;
        Progress progress = s.progress();
        List<InstalledCounter> kept = null;
        for (InstalledCounter installed : s.progress().counters()) {
            boolean activating = installed.activeTick() == tick;
            boolean removed = installed.removedTick() == tick;
            if (!activating && !removed) {
                continue;
            }
            if (removed) {
                if (kept == null) {
                    kept = new ArrayList<>(s.progress().counters());
                }
                kept.remove(installed);
            }
            Rules.CounterRules counter = rules.counter(installed.id());
            if (activating) {
                for (String insight : counter.def().grantsInsights()) {
                    progress = progress.withInsight(insight);
                }
            }
            for (Rules.Effect e : counter.variant(installed.option()).effects()) {
                if (e.kind().timing != EffectKind.Timing.STRUCTURAL) {
                    continue;
                }
                if (nodes == null) {
                    nodes = new EnumMap<>(ResourceId.class);
                    nodes.putAll(s.resources());
                }
                nodes.put(e.target(), upgrade(nodes.get(e.target()), e, removed ? -1 : 1));
            }
        }
        if (kept != null) {
            progress = progress.withCounters(kept);
        }
        GameState upgraded = nodes == null ? s : s.withResources(nodes);
        return progress == s.progress() ? upgraded : upgraded.withProgress(progress);
    }

    /** Toil comes out of the same crew as incident actions (decision AG). */
    private static void requireCrew(Progress p, CounterDef def) {
        if (p.ops().crew() < def.toil()) {
            throw new IllegalArgumentException(
                    "counter " + def.id() + " needs " + def.toil() + " crew, only " + p.ops().crew() + " left");
        }
    }

    private static InstalledCounter installed(Progress p, String id) {
        for (InstalledCounter c : p.counters()) {
            if (c.id().equals(id)) {
                return c;
            }
        }
        throw new IllegalArgumentException("counter " + id + " is not installed");
    }

    private static List<InstalledCounter> replace(Progress p, InstalledCounter changed) {
        List<InstalledCounter> next = new ArrayList<>(p.counters().size());
        for (InstalledCounter c : p.counters()) {
            next.add(c.id().equals(changed.id()) ? changed : c);
        }
        return next;
    }

    /** Applies ({@code sign} 1) or undoes ({@code sign} -1) a structural effect. */
    private static ResourceNode<?, ?> upgrade(ResourceNode<?, ?> node, Rules.Effect e, int sign) {
        int add = sign * (int) Math.round(e.value());
        return switch (node.model()) {
            case UtilizationModel m -> new ResourceNode<>(
                    new UtilizationModel(m.servers() + add, m.serviceRate(), m.queueLimit()),
                    (RateLoad) node.load(), (UtilizationState) node.state());
            case OccupancyModel m -> new ResourceNode<>(
                    new OccupancyModel(m.poolSize() + add, m.queueLimit()),
                    (HoldLoad) node.load(), (OccupancyState) node.state());
            case FillModel m -> {
                FillModel bigger = new FillModel(sign > 0 ? m.capacity() * e.value() : m.capacity() / e.value());
                yield new ResourceNode<>(bigger, (FlowLoad) node.load(),
                        bigger.stateAt(((FillState) node.state()).level()));
            }
        };
    }
}
