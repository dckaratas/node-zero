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
import java.util.EnumMap;

/** Buying counters and applying their one-off hardware changes when they become active. */
final class Counters {

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
        if (def.requiresInsight() != null && !p.insights().contains(def.requiresInsight())) {
            throw new IllegalArgumentException("counter " + def.id() + " needs insight " + def.requiresInsight());
        }
        int cost = def.cost().credits();
        if (p.credits() < cost) {
            throw new IllegalArgumentException(
                    "counter " + def.id() + " costs " + cost + " credits, only " + p.credits() + " left");
        }
        Rules.CounterRules.Variant variant = counter.variant(command.optionId());
        long activeTick = tick + Math.round(variant.applyTimeS() / SimConstants.DT);
        InstalledCounter installed = new InstalledCounter(def.id(), command.optionId(), tick, activeTick);
        return s.withProgress(p.withCredits(p.credits() - cost).withCounter(installed));
    }

    /** Applies structural effects (more cores, threads, disk) of counters that become active this tick. */
    GameState activate(GameState s, long tick) {
        EnumMap<ResourceId, ResourceNode<?, ?>> nodes = null;
        for (InstalledCounter installed : s.progress().counters()) {
            if (installed.activeTick() != tick) {
                continue;
            }
            for (Rules.Effect e : rules.counter(installed.id()).variant(installed.option()).effects()) {
                if (e.kind().timing != EffectKind.Timing.STRUCTURAL) {
                    continue;
                }
                if (nodes == null) {
                    nodes = new EnumMap<>(ResourceId.class);
                    nodes.putAll(s.resources());
                }
                nodes.put(e.target(), upgrade(nodes.get(e.target()), e));
            }
        }
        return nodes == null ? s : s.withResources(nodes);
    }

    private static ResourceNode<?, ?> upgrade(ResourceNode<?, ?> node, Rules.Effect e) {
        int add = (int) Math.round(e.value());
        return switch (node.model()) {
            case UtilizationModel m -> new ResourceNode<>(
                    new UtilizationModel(m.servers() + add, m.serviceRate(), m.queueLimit()),
                    (RateLoad) node.load(), (UtilizationState) node.state());
            case OccupancyModel m -> new ResourceNode<>(
                    new OccupancyModel(m.poolSize() + add, m.queueLimit()),
                    (HoldLoad) node.load(), (OccupancyState) node.state());
            case FillModel m -> {
                FillModel bigger = new FillModel(m.capacity() * e.value());
                yield new ResourceNode<>(bigger, (FlowLoad) node.load(),
                        bigger.stateAt(((FillState) node.state()).level()));
            }
        };
    }
}
