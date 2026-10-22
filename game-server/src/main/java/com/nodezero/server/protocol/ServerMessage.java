package com.nodezero.server.protocol;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.nodezero.sim.engine.Observation;
import com.nodezero.sim.state.ActionRun;
import com.nodezero.sim.state.WaveResult;
import com.nodezero.sim.state.WaveState;
import java.util.List;
import java.util.Map;

/** Messages the server sends, as JSON with a {@code type} field. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ServerMessage.Welcome.class, name = "welcome"),
    @JsonSubTypes.Type(value = ServerMessage.Snapshot.class, name = "snapshot"),
    @JsonSubTypes.Type(value = ServerMessage.Rejected.class, name = "rejected"),
    @JsonSubTypes.Type(value = ServerMessage.Paused.class, name = "paused"),
    @JsonSubTypes.Type(value = ServerMessage.Error.class, name = "error")
})
public sealed interface ServerMessage {

    /**
     * First message on a new connection.
     *
     * @param tickHz simulation rate
     * @param seed a 64-bit value, sent as a string: JavaScript numbers lose precision above 2^53
     * @param snapshotHz highest snapshot rate; the client interpolates between snapshots
     */
    record Welcome(String session, @JsonFormat(shape = JsonFormat.Shape.STRING) long seed, int tickHz, int snapshotHz)
            implements ServerMessage {}

    /**
     * The player's fog-of-war view ({@link Observation}), flattened. Sent only when something changed
     * ({@link SnapshotStream}); {@code insights}, {@code counters}, {@code codex}, {@code results} and
     * {@code actions} are omitted (null) when unchanged since the previous snapshot, so the client keeps its
     * last copy. {@code postmortem} is sent whenever one is open or just answered, and absent otherwise;
     * {@code chapter} likewise once the chapter is won.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Snapshot(
            long tick,
            int credits,
            int wave,
            WaveState.Phase phase,
            double shield,
            double burnRate,
            double shieldEmptyInS,
            Map<String, Double> metrics,
            List<String> signals,
            List<String> insights,
            List<Observation.Counter> counters,
            Observation.Postmortem postmortem,
            List<String> codex,
            List<WaveResult> results,
            Observation.Chapter chapter,
            int crew,
            List<ActionRun> actions) implements ServerMessage {

        /** Every field present: a keyframe. */
        public static Snapshot full(Observation o) {
            return new Snapshot(o.tick(), o.credits(), o.wave(), o.phase(), o.shield(), o.burnRate(), o.shieldEmptyInS(),
                    o.metrics(), o.signals(), o.insights(), o.counters(), o.postmortem(), o.codex(), o.results(),
                    o.chapter(), o.crew(), o.actions());
        }
    }

    /** A player action the rules did not allow (not enough credits, missing insight...). */
    record Rejected(ClientMessage action, String reason) implements ServerMessage {}

    record Paused(boolean paused) implements ServerMessage {}

    /** A message the server could not understand. */
    record Error(String reason) implements ServerMessage {}

    /** Seed and accepted actions by tick: enough to replay the session exactly (ADR-0006). */
    record ReplayLog(String session, @JsonFormat(shape = JsonFormat.Shape.STRING) long seed, List<Entry> actions) {
        public record Entry(long tick, ClientMessage action) {}
    }
}
