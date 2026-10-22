package com.nodezero.sim.state;

/**
 * The postmortem of a threat wave (ADR-0007). Opened when the wave ends, answered once, cleared when
 * the next wave starts. While it is unanswered the next wave cannot start.
 *
 * @param threatId the threat under review; never shown to the player directly (only its question)
 * @param wave the wave that brought it
 * @param chosenOption the player's answer, or {@code null} while pending
 */
public record PostmortemState(String threatId, int wave, String chosenOption) {

    public boolean answered() {
        return chosenOption != null;
    }

    public PostmortemState answer(String optionId) {
        return new PostmortemState(threatId, wave, optionId);
    }
}
