package com.nodezero.sim.state;

/**
 * How a wave ended, kept for the chapter summary (slice 7a, decision AE). A replayed wave replaces
 * its earlier result.
 *
 * @param survived {@code true} if the wave ran to its end, {@code false} if the shield broke
 * @param shield error budget left when it ended, in [0, 1]
 * @param earned credits the wave paid: reward minus upkeep (0 or negative for a failed wave)
 * @param postmortemRight the root-cause answer, or {@code null} for a quiet wave or one still unanswered
 */
public record WaveResult(int wave, boolean survived, double shield, int earned, Boolean postmortemRight) {

    public WaveResult withPostmortemRight(boolean right) {
        return new WaveResult(wave, survived, shield, earned, right);
    }
}
