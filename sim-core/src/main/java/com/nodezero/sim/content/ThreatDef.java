package com.nodezero.sim.content;

import com.nodezero.sim.state.ResourceId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A threat as written in {@code content/threats/*.yaml} (GDD §8). Structural rules are checked here,
 * at binding time, so errors carry the YAML line; cross-file rules live in {@link ContentLoader}.
 */
public record ThreatDef(
        String id,
        LocalizedText name,
        int chapter,
        boolean persists,
        Trigger trigger,
        DataGrowth data,
        Job job,
        Traffic traffic,
        List<Consumption> consumes,
        List<Effect> effects,
        Visibility visibility,
        List<String> counters,
        List<LockedCounter> lockedCounters,
        Postmortem postmortem,
        List<String> codex) {

    /** What the briefing says before a wave without a threat (slice 9c). */
    public static final String QUIET_BRIEFING_KEY = "briefing.quiet";

    public ThreatDef {
        Require.id(id, "id");
        Require.present(name, "name");
        Require.atLeast(chapter, 1, "chapter");
        Require.present(trigger, "trigger");
        Require.present(traffic, "traffic");
        consumes = Require.nonEmpty(consumes, "consumes");
        effects = Require.orEmpty(effects);
        Require.present(visibility, "visibility");
        counters = Require.nonEmpty(counters, "counters");
        counters.forEach(c -> Require.id(c, "counters[]"));
        lockedCounters = Require.orEmpty(lockedCounters);
        Require.present(postmortem, "postmortem");
        codex = Require.orEmpty(codex);
        codex.forEach(c -> Require.id(c, "codex[]"));

        if (trigger.warning() != null && trigger.warning().wave() >= trigger.wave()) {
            throw new IllegalArgumentException("trigger.warning.wave must come before trigger.wave");
        }
        boolean scalesWithRows = consumes.stream().anyMatch(c -> c.perRowMs() != null);
        if ((scalesWithRows || job != null) && data == null) {
            throw new IllegalArgumentException("per_row_ms and job need a data section");
        }
    }

    /** Every i18n key this threat references; each must exist in all languages. */
    public List<String> textKeys() {
        List<String> keys = new ArrayList<>();
        keys.add(trigger.briefingKey());
        if (trigger.warning() != null) {
            keys.add(trigger.warning().textKey());
        }
        lockedCounters.forEach(l -> keys.add(l.hintKey()));
        keys.add(postmortem.questionKey());
        postmortem.options().forEach(o -> keys.add(o.key()));
        return List.copyOf(keys);
    }

    /**
     * When the threat arrives. {@code briefing_key} names the event the player is told about before the wave
     * (a deploy, a campaign), never the root cause (slice 9c, ADR-0018 CA).
     */
    public record Trigger(int wave, String event, String briefingKey, Schedule schedule, Warning warning) {
        public Trigger {
            Require.atLeast(wave, 1, "trigger.wave");
            Require.id(event, "trigger.event");
            Require.textKey(briefingKey, "trigger.briefing_key");
        }
    }

    /** Cron schedule of a background job; {@code timezone} is whose clock the cron uses. */
    public record Schedule(String cron, String timezone) {
        public Schedule {
            if (Require.nonBlank(cron, "schedule.cron").trim().split("\\s+").length != 5) {
                throw new IllegalArgumentException("schedule.cron must have 5 fields: '" + cron + "'");
            }
            Require.id(timezone, "schedule.timezone");
        }
    }

    public record Warning(int wave, String channel, String textKey) {
        public Warning {
            Require.atLeast(wave, 1, "warning.wave");
            Require.snake(channel, "warning.channel");
            Require.textKey(textKey, "warning.text_key");
        }
    }

    /** Table growth that makes per-row costs worse over time (wave 4). */
    public record DataGrowth(String table, long initialRows, double rowsPerSecond) {
        public DataGrowth {
            Require.snake(table, "data.table");
            if (initialRows < 0) {
                throw new IllegalArgumentException("data.initial_rows must be >= 0: " + initialRows);
            }
            Require.nonNegative(rowsPerSecond, "data.rows_per_second");
        }
    }

    /**
     * Daily batch job (wave 5). Runs for rows / rows_per_minute game minutes, holding the slots of its
     * occupancy consumption and sending {@code io_rps} I/O operations of {@code io_service_time_ms} each.
     * {@code starts_after_s} (optional) delays the first run into the wave, so the player sees it coming
     * (ADR-0018 AV).
     */
    public record Job(double rowsPerMinute, double ioRps, double ioServiceTimeMs, Double startsAfterS) {
        public Job {
            Require.positive(rowsPerMinute, "job.rows_per_minute");
            Require.nonNegative(ioRps, "job.io_rps");
            Require.positive(ioServiceTimeMs, "job.io_service_time_ms");
            if (startsAfterS != null) {
                Require.nonNegative(startsAfterS, "job.starts_after_s");
            }
        }
    }

    public record Traffic(double baselineRps, List<Phase> shape, Mix mix) {
        public Traffic {
            Require.positive(baselineRps, "traffic.baseline_rps");
            shape = Require.nonEmpty(shape, "traffic.shape");
            Require.present(mix, "traffic.mix");
        }
    }

    public record Phase(PhaseKind phase, double durationS, Multiplier multiplier) {
        public Phase {
            Require.present(phase, "phase");
            Require.positive(durationS, "duration_s");
            Require.present(multiplier, "multiplier");
            if ((phase == PhaseKind.PLATEAU) != multiplier.isConstant()) {
                throw new IllegalArgumentException(
                        "plateau needs a constant multiplier, ramp/decay a range: " + phase);
            }
        }
    }

    public enum PhaseKind {
        RAMP,
        PLATEAU,
        DECAY
    }

    public record Mix(double read, double write) {
        public Mix {
            Require.nonNegative(read, "mix.read");
            Require.nonNegative(write, "mix.write");
            if (Math.abs(read + write - 1.0) > 1e-9) {
                throw new IllegalArgumentException("mix.read + mix.write must be 1: " + (read + write));
            }
        }
    }

    /**
     * What a threat costs a resource. Required fields depend on the model:
     * utilization → service_time_ms (per request), fill → units_per_request (per request),
     * occupancy → hold_time_ms + slots (a background job holding pool slots; per-request pool
     * usage is derived by the request pipeline, not declared).
     */
    public record Consumption(
            ResourceId resource,
            ModelKind model,
            Double serviceTimeMs,
            Double holdTimeMs,
            Integer slots,
            Double unitsPerRequest,
            Double perRowMs) {

        public Consumption {
            Require.present(resource, "resource");
            Require.present(model, "model");
            switch (model) {
                case UTILIZATION -> Require.positive(Require.present(serviceTimeMs, "service_time_ms"), "service_time_ms");
                case OCCUPANCY -> {
                    Require.positive(Require.present(holdTimeMs, "hold_time_ms"), "hold_time_ms");
                    Require.present(slots, "slots");
                }
                case FILL -> Require.positive(Require.present(unitsPerRequest, "units_per_request"), "units_per_request");
            }
            if (slots != null) {
                Require.atLeast(slots, 1, "slots");
            }
            if (perRowMs != null) {
                Require.nonNegative(perRowMs, "per_row_ms");
            }
        }
    }

    public record Effect(Condition when, String effect, String visual) {
        public Effect {
            Require.present(when, "when");
            Require.snake(effect, "effect");
            if (visual != null) {
                Require.snake(visual, "visual");
            }
        }
    }

    /** Fog of war: what the player sees with no monitoring, with Prometheus, and with Grafana. */
    public record Visibility(Unmonitored withoutMonitoring, List<String> prometheus, List<String> grafana) {
        public Visibility {
            Require.present(withoutMonitoring, "without_monitoring");
            prometheus = Require.orEmpty(prometheus);
            grafana = Require.orEmpty(grafana);
            prometheus.forEach(m -> Require.snake(m, "prometheus[]"));
            grafana.forEach(m -> Require.snake(m, "grafana[]"));
        }
    }

    public record Unmonitored(String signal, double delayS) {
        public Unmonitored {
            Require.snake(signal, "signal");
            Require.nonNegative(delayS, "delay_s");
        }
    }

    public record LockedCounter(String id, int unlocksInChapter, String hintKey) {
        public LockedCounter {
            Require.id(id, "locked_counters[].id");
            Require.atLeast(unlocksInChapter, 1, "unlocks_in_chapter");
            Require.textKey(hintKey, "hint_key");
        }
    }

    public record Postmortem(String questionKey, List<Option> options, Reward reward) {
        public Postmortem {
            Require.textKey(questionKey, "question_key");
            options = Require.nonEmpty(options, "options");
            if (options.size() < 2) {
                throw new IllegalArgumentException("postmortem needs at least 2 options");
            }
            Set<String> ids = new LinkedHashSet<>();
            options.forEach(o -> {
                if (!ids.add(o.id())) {
                    throw new IllegalArgumentException("duplicate postmortem option id: " + o.id());
                }
            });
            if (options.stream().filter(Option::correct).count() != 1) {
                throw new IllegalArgumentException("exactly one postmortem option must be correct");
            }
        }
    }

    public record Option(String id, String key, boolean correct) {
        public Option {
            Require.snake(id, "options[].id");
            Require.textKey(key, "options[].key");
        }
    }

    public record Reward(String unlock) {
        public Reward {
            Require.id(unlock, "reward.unlock");
        }
    }
}
