package com.nodezero.sim.engine;

import com.nodezero.sim.state.PostmortemState;
import com.nodezero.sim.state.WaveResult;
import java.util.List;

/** The chapter summary (slice 7a, decision AE). No game over: a failed wave is replayed until it is won. */
final class ChapterScore {

    /** An average shield at least this high earns the second star. */
    static final double SHIELD_STAR = 0.5;

    private ChapterScore() {}

    /**
     * @param results wave results by wave number
     * @param postmortem the open postmortem; the chapter is not over while it waits for an answer
     * @return the summary once waves 1..{@code lastWave} are all survived and reviewed, otherwise {@code null}
     */
    static Observation.Chapter summary(int chapter, int lastWave, List<WaveResult> results, PostmortemState postmortem) {
        if (lastWave < 1 || results.size() < lastWave || (postmortem != null && !postmortem.answered())) {
            return null;
        }
        double shield = 0;
        int right = 0;
        int asked = 0;
        for (int wave = 1; wave <= lastWave; wave++) {
            WaveResult r = results.get(wave - 1);
            if (r.wave() != wave || !r.survived()) {
                return null;
            }
            shield += r.shield();
            if (r.postmortemRight() != null) {
                asked++;
                if (r.postmortemRight()) {
                    right++;
                }
            }
        }
        double average = shield / lastWave;
        int stars = 1 + (average >= SHIELD_STAR ? 1 : 0) + (right == asked ? 1 : 0);
        return new Observation.Chapter(chapter, stars, average, right, asked);
    }
}
