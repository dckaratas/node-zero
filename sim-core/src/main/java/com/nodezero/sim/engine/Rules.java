package com.nodezero.sim.engine;

import com.nodezero.sim.content.Content;
import com.nodezero.sim.content.ModelKind;
import com.nodezero.sim.content.ThreatDef;
import com.nodezero.sim.model.FillModel;
import com.nodezero.sim.model.OccupancyModel;
import com.nodezero.sim.model.ResourceModel;
import com.nodezero.sim.model.UtilizationModel;
import com.nodezero.sim.state.ResourceId;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything static a game needs: station hardware and validated content. Checked once at
 * construction so the per-tick path never has to.
 */
public record Rules(StationLayout station, Content content) {

    /** Resources the request pipeline reads and the model kind it expects for each. */
    private static final List<Required> PIPELINE = List.of(
            new Required(ResourceId.CPU, ModelKind.UTILIZATION),
            new Required(ResourceId.THREADS, ModelKind.OCCUPANCY),
            new Required(ResourceId.DB_POOL, ModelKind.OCCUPANCY),
            new Required(ResourceId.DISK, ModelKind.FILL),
            new Required(ResourceId.DB_CPU, ModelKind.UTILIZATION),
            new Required(ResourceId.DB_IO, ModelKind.UTILIZATION));

    public Rules {
        List<String> problems = new ArrayList<>();
        for (Required r : PIPELINE) {
            ResourceModel<?, ?> model = station.models().get(r.resource());
            if (model == null || kindOf(model) != r.kind()) {
                problems.add("station needs " + r.resource() + " as a " + r.kind() + " model");
            }
        }
        for (ThreatDef threat : content.threats().values()) {
            for (ThreatDef.Consumption c : threat.consumes()) {
                ResourceModel<?, ?> model = station.models().get(c.resource());
                if (model != null && kindOf(model) != c.model()) {
                    problems.add("threat " + threat.id() + ": " + c.resource() + " is "
                            + kindOf(model) + " in the station, not " + c.model());
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("invalid rules:\n  - " + String.join("\n  - ", problems));
        }
    }

    /** The threat a wave brings, or {@code null} for a quiet wave. */
    public ThreatDef threatForWave(int wave) {
        for (ThreatDef threat : content.threats().values()) {
            if (threat.trigger().wave() == wave) {
                return threat;
            }
        }
        return null;
    }

    static ModelKind kindOf(ResourceModel<?, ?> model) {
        return switch (model) {
            case UtilizationModel m -> ModelKind.UTILIZATION;
            case OccupancyModel m -> ModelKind.OCCUPANCY;
            case FillModel m -> ModelKind.FILL;
        };
    }

    private record Required(ResourceId resource, ModelKind kind) {}
}
