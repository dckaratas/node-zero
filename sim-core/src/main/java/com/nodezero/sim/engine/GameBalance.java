package com.nodezero.sim.engine;

/**
 * Tunable game economy and SLO numbers (GDD §5). First-pass values; balancing comes with playtests.
 *
 * @param startCredits credits at the start of the chapter
 * @param slo availability target; the error budget of a wave is (1 - slo) of its expected requests
 * @param latencySloMs requests served slower than this count against the budget (ADR-0007)
 * @param waveReward credits for a wave survived with a full shield, scaled by the shield left
 * @param quietWaveSeconds length of a wave that brings no threat
 * @param endWaveOnEmptyShield false for practice: the wave plays out even with an empty shield
 */
public record GameBalance(
        int startCredits,
        double slo,
        double latencySloMs,
        int waveReward,
        double quietWaveSeconds,
        boolean endWaveOnEmptyShield) {

    public static final GameBalance DEFAULT = new GameBalance(1000, 0.99, 500, 300, 60, true);

    /** Waves never fail early; used to study the station's behaviour under a whole wave. */
    public static final GameBalance PRACTICE = new GameBalance(1000, 0.99, 500, 300, 60, false);
}
