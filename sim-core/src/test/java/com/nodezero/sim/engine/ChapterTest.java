package com.nodezero.sim.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nodezero.sim.engine.Command.AnswerPostmortem;
import com.nodezero.sim.engine.Command.StartWave;
import com.nodezero.sim.state.GameState;
import com.nodezero.sim.state.PostmortemState;
import com.nodezero.sim.state.WaveResult;
import com.nodezero.sim.state.WaveState;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Shield burn rate, wave results and the chapter summary (slice 7a, decisions AD/AE). */
class ChapterTest {

    private static final Game PRACTICE = Game.chapterOne(GameBalance.PRACTICE);
    private static final Game DEFAULT = Game.chapterOne(GameBalance.DEFAULT);
    /** Right root cause of each threat wave of chapter 1. */
    private static final String[] RIGHT = {null, null, "b", "b", "c", "c"};

    private static GameState playWave(Game game, GameState s, int wave) {
        long tick = s.tick() + 1;
        s = game.step(s, List.of(new StartWave(wave)), tick);
        while (s.wave().phase() == WaveState.Phase.RUNNING) {
            s = game.step(s, List.of(), ++tick);
        }
        return s;
    }

    private static GameState answer(Game game, GameState s, String option) {
        return game.step(s, List.of(new AnswerPostmortem(option)), s.tick() + 1);
    }

    @Test
    void theShieldShowsHowFastItBurnsAndWhenItBreaks() {
        Observation idle = DEFAULT.observe(DEFAULT.newState(1));
        assertThat(idle.burnRate()).isZero();
        assertThat(idle.shieldEmptyInS()).isEqualTo(-1);

        GameState s = DEFAULT.step(DEFAULT.newState(1), List.of(new StartWave(3)), 1); // traffic surge, no counters
        Observation burning = null;
        for (long tick = 2; s.wave().phase() == WaveState.Phase.RUNNING; tick++) {
            s = DEFAULT.step(s, List.of(), tick);
            Observation o = DEFAULT.observe(s);
            if (o.burnRate() > 1) {
                burning = o;
                break;
            }
        }

        assertThat(burning).as("an unprepared surge burns faster than the budget lasts").isNotNull();
        assertThat(burning.shieldEmptyInS()).isPositive();
        assertThat(burning.shieldEmptyInS()).isLessThan(s.wave().durationS());
    }

    @Test
    void aBrokenShieldFailsTheWaveAndIsRecorded() {
        GameState s = playWave(DEFAULT, DEFAULT.newState(1), 3);

        assertThat(s.wave().phase()).isEqualTo(WaveState.Phase.FAILED);
        assertThat(s.progress().results()).containsExactly(new WaveResult(3, false, 0, 0, null));
    }

    @Test
    void wonWavesRecordShieldEarningsAndThePostmortemVerdict() {
        GameState s = playWave(PRACTICE, PRACTICE.newState(1), 1);
        int before = s.progress().credits();
        s = playWave(PRACTICE, s, 2);
        WaveResult logStorm = s.progress().results().get(1);
        assertThat(logStorm.survived()).isTrue();
        assertThat(logStorm.earned()).isEqualTo(s.progress().credits() - before);
        assertThat(logStorm.postmortemRight()).as("not answered yet").isNull();

        s = answer(PRACTICE, s, "c"); // wrong
        assertThat(s.progress().results()).extracting(WaveResult::wave).containsExactly(1, 2);
        assertThat(s.progress().results().get(0).postmortemRight()).as("quiet wave").isNull();
        assertThat(s.progress().results().get(1).postmortemRight()).isFalse();

        s = playWave(PRACTICE, s, 2);
        assertThat(s.progress().results()).hasSize(2);
        assertThat(s.progress().results().get(1).postmortemRight())
                .as("a replayed wave keeps its one verdict").isFalse();
    }

    @Test
    void theChapterEndsWhenTheLastWaveIsWonAndReviewed() {
        GameState s = PRACTICE.newState(1);
        for (int wave = 1; wave <= 5; wave++) {
            s = playWave(PRACTICE, s, wave);
            if (RIGHT[wave] != null) {
                if (wave == 5) {
                    assertThat(PRACTICE.observe(s).chapter()).as("waits for the last postmortem").isNull();
                }
                s = answer(PRACTICE, s, RIGHT[wave]);
            }
        }

        Observation.Chapter chapter = PRACTICE.observe(s).chapter();
        assertThat(chapter).isNotNull();
        assertThat(chapter.number()).isEqualTo(1);
        assertThat(chapter.postmortems()).isEqualTo(4);
        assertThat(chapter.postmortemsRight()).isEqualTo(4);
        assertThat(chapter.stars()).isEqualTo(chapter.averageShield() >= ChapterScore.SHIELD_STAR ? 3 : 2);

        GameState done = s;
        assertThatThrownBy(() -> PRACTICE.step(done, List.of(new StartWave(6)), done.tick() + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chapter 1 ends at wave 5");
    }

    @Test
    void starsCountFinishingShieldAndRootCauses() {
        List<WaveResult> strong = List.of(
                new WaveResult(1, true, 1, 500, null),
                new WaveResult(2, true, 0.8, 400, true),
                new WaveResult(3, true, 0.6, 300, true));
        assertThat(ChapterScore.summary(1, 3, strong, null).stars()).isEqualTo(3);

        List<WaveResult> weak = List.of(
                new WaveResult(1, true, 0.4, 200, null),
                new WaveResult(2, true, 0.2, 100, false),
                new WaveResult(3, true, 0.1, 50, true));
        Observation.Chapter chapter = ChapterScore.summary(1, 3, weak, null);
        assertThat(chapter.stars()).isEqualTo(1);
        assertThat(chapter.postmortemsRight()).isEqualTo(1);
        assertThat(chapter.postmortems()).isEqualTo(2);

        List<WaveResult> lost = List.of(strong.get(0), strong.get(1), new WaveResult(3, false, 0, 0, null));
        assertThat(ChapterScore.summary(1, 3, lost, null)).as("a failed wave must be replayed").isNull();
        assertThat(ChapterScore.summary(1, 3, strong.subList(0, 2), null)).isNull();
        assertThat(ChapterScore.summary(1, 3, strong, new PostmortemState("t", 3, null))).isNull();
    }
}
