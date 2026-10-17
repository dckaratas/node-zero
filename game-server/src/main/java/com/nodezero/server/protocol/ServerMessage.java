package com.nodezero.server.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.nodezero.sim.engine.Observation;
import java.util.List;

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

    /** First message on a new connection. */
    record Welcome(String session, long seed, int tickHz) implements ServerMessage {}

    /** The player's full fog-of-war view, once per tick (decision M: full view first, deltas if needed). */
    record Snapshot(Observation observation) implements ServerMessage {}

    /** A player action the rules did not allow (not enough credits, missing insight...). */
    record Rejected(ClientMessage action, String reason) implements ServerMessage {}

    record Paused(boolean paused) implements ServerMessage {}

    /** A message the server could not understand. */
    record Error(String reason) implements ServerMessage {}

    /** Seed and accepted actions by tick: enough to replay the session exactly (ADR-0006). */
    record ReplayLog(String session, long seed, List<Entry> actions) {
        public record Entry(long tick, ClientMessage action) {}
    }
}
