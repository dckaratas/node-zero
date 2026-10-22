package com.nodezero.sim.engine;

import com.nodezero.sim.content.ActionDef;
import com.nodezero.sim.model.FillModel;
import com.nodezero.sim.model.FillState;
import com.nodezero.sim.model.FlowLoad;
import com.nodezero.sim.model.HoldLoad;
import com.nodezero.sim.model.OccupancyModel;
import com.nodezero.sim.model.OccupancyState;
import com.nodezero.sim.model.RateLoad;
import com.nodezero.sim.model.UtilizationModel;
import com.nodezero.sim.model.UtilizationState;
import com.nodezero.sim.state.ActionRun;
import com.nodezero.sim.state.ActiveThreat;
import com.nodezero.sim.state.GameState;
import com.nodezero.sim.state.OpsState;
import com.nodezero.sim.state.Progress;
import com.nodezero.sim.state.ResourceId;
import com.nodezero.sim.state.ResourceNode;
import com.nodezero.sim.state.WaveState;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * Incident actions (slice 7b, ADR-0018 AF/AG/AH): checked against crew and cooldown, then their instant
 * effects change the state right away. Windowed effects (downtime, cold JVM, paused job) are read by
 * {@link Pipeline} from the recorded run. Requests an action drops count against the running wave.
 */
final class Actions {

    private final Rules rules;

    Actions(Rules rules) {
        this.rules = rules;
    }

    GameState run(GameState s, Command.RunAction command, long tick) {
        Rules.ActionRules action = rules.action(command.actionId());
        ActionDef def = action.def();
        List<Rules.Step> steps = action.steps(command.optionId());
        OpsState ops = s.progress().ops();
        ActionRun last = ops.run(def.id());
        if (last != null && tick < last.readyTick()) {
            long seconds = (long) Math.ceil((last.readyTick() - tick) * SimConstants.DT);
            throw new IllegalArgumentException("action " + def.id() + " is cooling down for " + seconds + " s");
        }
        if (ops.crew() < def.toil()) {
            throw new IllegalArgumentException(
                    "action " + def.id() + " needs " + def.toil() + " crew, only " + ops.crew() + " left");
        }
        ActionRun run = new ActionRun(def.id(), command.optionId(), tick,
                tick + action.durationTicks(command.optionId()), tick + action.cooldownTicks());
        GameState next = s.withProgress(s.progress()
                .withOps(ops.withCrew(ops.crew() - def.toil()).withRun(run))
                .withCodex(def.codex()));
        for (Rules.Step step : steps) {
            if (step.instant()) {
                next = apply(next, step.effect());
            }
        }
        return next;
    }

    private GameState apply(GameState s, Rules.Effect e) {
        Progress p = s.progress();
        OpsState ops = p.ops();
        return switch (e.kind()) {
            case RESET_QUEUES -> {
                // Everything inside the application is lost: in-flight requests and the ones queued for a thread.
                OccupancyState threads = occupancy(s, ResourceId.THREADS);
                GameState dropped = fail(s, threads.active() + threads.pending());
                EnumMap<ResourceId, ResourceNode<?, ?>> nodes = nodes(dropped);
                nodes.put(ResourceId.THREADS, idlePool(dropped, ResourceId.THREADS));
                nodes.put(ResourceId.DB_POOL, idlePool(dropped, ResourceId.DB_POOL));
                nodes.put(ResourceId.CPU, idleCpu(dropped));
                yield dropped.withResources(nodes);
            }
            case CANCEL_QUERIES -> {
                OccupancyState pool = occupancy(s, ResourceId.DB_POOL);
                GameState cancelled = fail(s, pool.active() + pool.pending());
                EnumMap<ResourceId, ResourceNode<?, ?>> nodes = nodes(cancelled);
                nodes.put(ResourceId.DB_POOL, idlePool(cancelled, ResourceId.DB_POOL));
                yield cancelled.withResources(nodes);
            }
            case FREE_LOGS -> withDiskLevel(s, disk(s).level() - e.value() * logsMb(s));
            case ORPHAN_LOGS -> {
                // rm unlinks the name; the application still holds the file open, so df shows no change.
                double orphaned = e.value() * logsMb(s);
                yield s.withProgress(p.withOps(ops.withOrphanedMb(ops.orphanedMb() + orphaned)));
            }
            case FREE_ORPHANED_DISK -> {
                GameState freed = withDiskLevel(s, disk(s).level() - ops.orphanedMb());
                yield freed.withProgress(freed.progress().withOps(ops.withOrphanedMb(0)));
            }
            case FREE_ARCHIVES -> {
                if (ops.archivesCleared()) {
                    yield s;
                }
                GameState freed = withDiskLevel(s, disk(s).level() - e.value());
                yield freed.withProgress(freed.progress().withOps(ops.withArchivesCleared()));
            }
            case ROLLBACK_DEPLOY -> {
                List<ActiveThreat> kept = new ArrayList<>();
                for (ActiveThreat t : p.activeThreats()) {
                    if (!"deploy".equals(rules.content().threat(t.id()).trigger().event())) {
                        kept.add(t);
                    }
                }
                yield kept.size() == p.activeThreats().size() ? s : s.withProgress(p.withActiveThreats(kept));
            }
            default -> s; // narrative effects; windowed ones are read by the pipeline
        };
    }

    /** Requests an action drops count against the running wave's budget. */
    private static GameState fail(GameState s, double requests) {
        WaveState wave = s.progress().wave();
        if (wave.phase() != WaveState.Phase.RUNNING || requests <= 0) {
            return s;
        }
        return s.withProgress(s.progress().withWave(wave.withExtraFailures(requests)));
    }

    /** Log data on the disk: what was written since the game started, minus what deleted-but-open files hold. */
    private double logsMb(GameState s) {
        return Math.max(0, disk(s).level() - rules.station().initialDiskLevel() - s.progress().ops().orphanedMb());
    }

    @SuppressWarnings("unchecked")
    private static GameState withDiskLevel(GameState s, double level) {
        ResourceNode<FillState, FlowLoad> node = (ResourceNode<FillState, FlowLoad>) s.resource(ResourceId.DISK);
        FillModel model = (FillModel) node.model();
        EnumMap<ResourceId, ResourceNode<?, ?>> nodes = nodes(s);
        nodes.put(ResourceId.DISK, new ResourceNode<>(model, node.load(), model.stateAt(Math.max(0, level))));
        return s.withResources(nodes);
    }

    private static ResourceNode<?, ?> idlePool(GameState s, ResourceId id) {
        return new ResourceNode<>((OccupancyModel) s.resource(id).model(), new HoldLoad(0, 1), OccupancyState.initial());
    }

    private static ResourceNode<?, ?> idleCpu(GameState s) {
        return new ResourceNode<>((UtilizationModel) s.resource(ResourceId.CPU).model(), new RateLoad(0),
                UtilizationState.initial());
    }

    private static EnumMap<ResourceId, ResourceNode<?, ?>> nodes(GameState s) {
        EnumMap<ResourceId, ResourceNode<?, ?>> nodes = new EnumMap<>(ResourceId.class);
        nodes.putAll(s.resources());
        return nodes;
    }

    private static OccupancyState occupancy(GameState s, ResourceId id) {
        return (OccupancyState) s.resource(id).state();
    }

    private static FillState disk(GameState s) {
        return (FillState) s.resource(ResourceId.DISK).state();
    }
}
