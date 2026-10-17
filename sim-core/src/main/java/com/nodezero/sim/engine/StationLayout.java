package com.nodezero.sim.engine;

import com.nodezero.sim.model.FillModel;
import com.nodezero.sim.model.FlowLoad;
import com.nodezero.sim.model.HoldLoad;
import com.nodezero.sim.model.OccupancyModel;
import com.nodezero.sim.model.OccupancyState;
import com.nodezero.sim.model.RateLoad;
import com.nodezero.sim.model.ResourceModel;
import com.nodezero.sim.model.UtilizationModel;
import com.nodezero.sim.model.UtilizationState;
import com.nodezero.sim.state.GameState;
import com.nodezero.sim.state.ResourceId;
import com.nodezero.sim.state.ResourceNode;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Hardware and baseline workload of a station. Balancing numbers are a first pass (GDD §5).
 *
 * @param models one model per resource; the pipeline needs all six resources of NODE-01
 * @param baselineRps traffic outside of waves
 * @param writeFraction share of requests that write (they also hit DB I/O)
 * @param logUnitsPerRequest log data per request at INFO level, in MB
 * @param initialDiskLevel disk already used when the game starts, in MB
 */
public record StationLayout(
        Map<ResourceId, ResourceModel<?, ?>> models,
        double baselineRps,
        double writeFraction,
        double logUnitsPerRequest,
        double initialDiskLevel) {

    public StationLayout {
        EnumMap<ResourceId, ResourceModel<?, ?>> copy = new EnumMap<>(ResourceId.class);
        copy.putAll(models);
        models = Collections.unmodifiableMap(copy);
    }

    /** NODE-01: 4 cores x 50 rps, Tomcat 200/100, HikariCP 10, a 4-core database and a 100 MB disk. */
    public static StationLayout node01() {
        EnumMap<ResourceId, ResourceModel<?, ?>> models = new EnumMap<>(ResourceId.class);
        models.put(ResourceId.CPU, new UtilizationModel(4, 50, 100));
        models.put(ResourceId.THREADS, new OccupancyModel(200, 100));
        models.put(ResourceId.DB_POOL, new OccupancyModel(10, 200));
        models.put(ResourceId.DISK, new FillModel(100));
        models.put(ResourceId.DB_CPU, new UtilizationModel(4, 200, 1000));
        models.put(ResourceId.DB_IO, new UtilizationModel(1, 500, 1000));
        return new StationLayout(models, 100, 0.2, 0.0002, 75);
    }

    public GameState initialState(long seed) {
        EnumMap<ResourceId, ResourceNode<?, ?>> nodes = new EnumMap<>(ResourceId.class);
        models.forEach((id, model) -> nodes.put(id, idle(model)));
        return new GameState(0, seed, nodes);
    }

    private ResourceNode<?, ?> idle(ResourceModel<?, ?> model) {
        return switch (model) {
            case UtilizationModel m -> new ResourceNode<>(m, new RateLoad(0), UtilizationState.initial());
            case OccupancyModel m -> new ResourceNode<>(m, new HoldLoad(0, 1), OccupancyState.initial());
            case FillModel m -> new ResourceNode<>(m, new FlowLoad(0, 0, 0), m.stateAt(initialDiskLevel));
        };
    }
}
