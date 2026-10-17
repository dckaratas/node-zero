package com.nodezero.server.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.nodezero.sim.engine.Command;

/**
 * Messages a client may send, as JSON with a {@code type} field (decision M), e.g.
 * {@code {"type":"apply_counter","counter":"create-index","option":"concurrently"}}.
 * Only player actions exist here; test-only commands (UnlockInsight, SetArrivalRate) are not exposed.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ClientMessage.StartWave.class, name = "start_wave"),
    @JsonSubTypes.Type(value = ClientMessage.ApplyCounter.class, name = "apply_counter"),
    @JsonSubTypes.Type(value = ClientMessage.Pause.class, name = "pause"),
    @JsonSubTypes.Type(value = ClientMessage.Resume.class, name = "resume")
})
public sealed interface ClientMessage {

    record StartWave(int wave) implements ClientMessage {}

    record ApplyCounter(String counter, String option) implements ClientMessage {}

    record Pause() implements ClientMessage {}

    record Resume() implements ClientMessage {}

    /** The simulation command for a player action; {@code null} for session controls (pause/resume). */
    static Command toCommand(ClientMessage message) {
        return switch (message) {
            case StartWave m -> new Command.StartWave(m.wave());
            case ApplyCounter m -> new Command.ApplyCounter(m.counter(), m.option());
            case Pause m -> null;
            case Resume m -> null;
        };
    }

    /** The protocol form of a logged command, for replay export. */
    static ClientMessage fromCommand(Command command) {
        return switch (command) {
            case Command.StartWave c -> new StartWave(c.number());
            case Command.ApplyCounter c -> new ApplyCounter(c.counterId(), c.optionId());
            case Command.Noop c -> throw notAPlayerAction(c);
            case Command.SetArrivalRate c -> throw notAPlayerAction(c);
            case Command.SetHoldTime c -> throw notAPlayerAction(c);
            case Command.UnlockInsight c -> throw notAPlayerAction(c);
        };
    }

    private static IllegalArgumentException notAPlayerAction(Command command) {
        return new IllegalArgumentException("not a player action: " + command);
    }
}
