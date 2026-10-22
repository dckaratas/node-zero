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
    @JsonSubTypes.Type(value = ClientMessage.AnswerPostmortem.class, name = "answer_postmortem"),
    @JsonSubTypes.Type(value = ClientMessage.RunAction.class, name = "run_action"),
    @JsonSubTypes.Type(value = ClientMessage.RemoveCounter.class, name = "remove_counter"),
    @JsonSubTypes.Type(value = ClientMessage.ConfigureCounter.class, name = "configure_counter"),
    @JsonSubTypes.Type(value = ClientMessage.Pause.class, name = "pause"),
    @JsonSubTypes.Type(value = ClientMessage.Resume.class, name = "resume")
})
public sealed interface ClientMessage {

    record StartWave(int wave) implements ClientMessage {}

    record ApplyCounter(String counter, String option) implements ClientMessage {}

    /** The root cause the player picks in the open postmortem; one attempt. */
    record AnswerPostmortem(String option) implements ClientMessage {}

    /** An incident action (slice 7b), optionally one of its options. */
    record RunAction(String action, String option) implements ClientMessage {}

    /** Undo an installed counter (slice 7c). */
    record RemoveCounter(String counter) implements ClientMessage {}

    /** New value for the setting that drives a counter's effect (slice 7c). */
    record ConfigureCounter(String counter, double value) implements ClientMessage {}

    record Pause() implements ClientMessage {}

    record Resume() implements ClientMessage {}

    /** The simulation command for a player action; {@code null} for session controls (pause/resume). */
    static Command toCommand(ClientMessage message) {
        return switch (message) {
            case StartWave m -> new Command.StartWave(m.wave());
            case ApplyCounter m -> new Command.ApplyCounter(m.counter(), m.option());
            case AnswerPostmortem m -> new Command.AnswerPostmortem(m.option());
            case RunAction m -> new Command.RunAction(m.action(), m.option());
            case RemoveCounter m -> new Command.RemoveCounter(m.counter());
            case ConfigureCounter m -> new Command.ConfigureCounter(m.counter(), m.value());
            case Pause m -> null;
            case Resume m -> null;
        };
    }

    /** The protocol form of a logged command, for replay export. */
    static ClientMessage fromCommand(Command command) {
        return switch (command) {
            case Command.StartWave c -> new StartWave(c.number());
            case Command.ApplyCounter c -> new ApplyCounter(c.counterId(), c.optionId());
            case Command.AnswerPostmortem c -> new AnswerPostmortem(c.optionId());
            case Command.RunAction c -> new RunAction(c.actionId(), c.optionId());
            case Command.RemoveCounter c -> new RemoveCounter(c.counterId());
            case Command.ConfigureCounter c -> new ConfigureCounter(c.counterId(), c.value());
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
