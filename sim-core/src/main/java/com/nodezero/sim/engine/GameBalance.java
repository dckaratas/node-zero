package com.nodezero.sim.engine;

/**
 * Tunable game economy and SLO numbers (GDD §5). First-pass values; balancing comes with playtests.
 *
 * @param startCredits credits at the start of the chapter
 * @param slo availability target; each wave starts with the error budget of one SLO window: (1 - slo) of its requests
 * @param budgetWindowS length of that window, in game seconds at baseline traffic (decision AK: one station day,
 *     1440 s = 24 game hours); a full outage at baseline traffic empties it in (1 - slo) of the window
 * @param latencySloMs requests served slower than this count against the budget (ADR-0007, decision AL)
 * @param waveReward credits for a wave survived with a full shield, scaled by the shield left
 * @param quietWaveSeconds length of a wave that brings no threat
 * @param endWaveOnEmptyShield false for practice: the wave plays out even with an empty shield
 * @param postmortemBonus credits for naming the right root cause in a postmortem (decision AA)
 */
public record GameBalance(
        int startCredits,
        double slo,
        double budgetWindowS,
        double latencySloMs,
        int waveReward,
        double quietWaveSeconds,
        boolean endWaveOnEmptyShield,
        int postmortemBonus) {

    public static final GameBalance DEFAULT = new GameBalance(1000, 0.99, 1440, 500, 300, 60, true, 100);

    /** Waves never fail early; used to study the station's behaviour under a whole wave. */
    public static final GameBalance PRACTICE = new GameBalance(1000, 0.99, 1440, 500, 300, 60, false, 100);
}
