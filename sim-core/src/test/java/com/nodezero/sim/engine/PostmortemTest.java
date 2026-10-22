package com.nodezero.sim.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nodezero.sim.engine.Command.AnswerPostmortem;
import com.nodezero.sim.engine.Command.StartWave;
import com.nodezero.sim.state.GameState;
import com.nodezero.sim.state.WaveState;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Postmortem after a threat wave (slice 6a, decisions Z/AA; ADR-0007, ADR-0008). */
class PostmortemTest {

    private static final Game PRACTICE = Game.chapterOne(GameBalance.PRACTICE);
    private static final Game DEFAULT = Game.chapterOne(GameBalance.DEFAULT);

    /** Starts the wave at the next tick and plays until it is no longer running. */
    private static GameState playWave(Game game, GameState s, int wave) {
        long tick = s.tick() + 1;
        s = game.step(s, List.of(new StartWave(wave)), tick);
        while (s.wave().phase() == WaveState.Phase.RUNNING) {
            s = game.step(s, List.of(), ++tick);
        }
        return s;
    }

    private static GameState step(Game game, GameState s, Command command) {
        return game.step(s, List.of(command), s.tick() + 1);
    }

    @Test
    void aThreatWaveEndsWithAQuestionWhoseAnswerStaysHidden() {
        GameState s = playWave(PRACTICE, PRACTICE.newState(1), 2); // log-storm

        Observation.Postmortem pm = PRACTICE.observe(s).postmortem();
        assertThat(s.wave().phase()).isEqualTo(WaveState.Phase.COMPLETE);
        assertThat(pm.wave()).isEqualTo(2);
        assertThat(pm.questionKey()).isEqualTo("pm.log_storm.q");
        assertThat(pm.options()).extracting(Observation.Choice::id).containsExactly("a", "b", "c", "d");
        assertThat(pm.chosen()).isNull();
        assertThat(pm.correct()).as("fog of war: the answer is not sent before the player answers").isNull();
    }

    @Test
    void theNextWaveWaitsForTheAnswer() {
        GameState s = playWave(PRACTICE, PRACTICE.newState(1), 2);
        GameState pending = s;

        assertThatThrownBy(() -> step(PRACTICE, pending, new StartWave(3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("postmortem of wave 2");
    }

    @Test
    void theRightRootCauseEarnsTheBonusAndRevealsTheAnswer() {
        GameState s = playWave(PRACTICE, PRACTICE.newState(1), 2);
        int before = s.progress().credits();

        s = step(PRACTICE, s, new AnswerPostmortem("b"));

        Observation.Postmortem pm = PRACTICE.observe(s).postmortem();
        assertThat(s.progress().credits()).isEqualTo(before + GameBalance.PRACTICE.postmortemBonus());
        assertThat(pm.chosen()).isEqualTo("b");
        assertThat(pm.correct()).isEqualTo("b");

        s = step(PRACTICE, s, new StartWave(3));
        assertThat(PRACTICE.observe(s).postmortem()).as("cleared when the next wave starts").isNull();
    }

    @Test
    void aWrongAnswerCostsNothingButTheBonus() {
        GameState s = playWave(PRACTICE, PRACTICE.newState(1), 2);
        int before = s.progress().credits();

        s = step(PRACTICE, s, new AnswerPostmortem("c"));

        Observation.Postmortem pm = PRACTICE.observe(s).postmortem();
        assertThat(s.progress().credits()).isEqualTo(before);
        assertThat(pm.chosen()).isEqualTo("c");
        assertThat(pm.correct()).isEqualTo("b");
    }

    @Test
    void thereIsExactlyOneAttempt() {
        GameState answered = step(PRACTICE, playWave(PRACTICE, PRACTICE.newState(1), 2), new AnswerPostmortem("c"));
        GameState fresh = PRACTICE.newState(1);
        GameState pending = playWave(PRACTICE, fresh, 2);

        assertThatThrownBy(() -> step(PRACTICE, answered, new AnswerPostmortem("b")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already answered");
        assertThatThrownBy(() -> step(PRACTICE, fresh, new AnswerPostmortem("b")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no postmortem");
        assertThatThrownBy(() -> step(PRACTICE, pending, new AnswerPostmortem("z")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unknown postmortem option");
    }

    @Test
    void quietWavesHaveNoPostmortem() {
        GameState s = playWave(PRACTICE, PRACTICE.newState(1), 1); // no threat triggers on wave 1

        assertThat(PRACTICE.observe(s).postmortem()).isNull();
        assertThat(step(PRACTICE, s, new StartWave(2)).wave().phase()).isEqualTo(WaveState.Phase.RUNNING);
    }

    @Test
    void aFailedWaveIsReviewedOnceEvenIfItIsRetried() {
        GameState s = playWave(DEFAULT, DEFAULT.newState(1), 3); // festival without counters: shield runs out
        assertThat(s.wave().phase()).isEqualTo(WaveState.Phase.FAILED);
        assertThat(DEFAULT.observe(s).postmortem().questionKey()).isEqualTo("pm.traffic_surge.q");

        s = step(DEFAULT, s, new AnswerPostmortem("b"));
        s = playWave(DEFAULT, s, 3);

        assertThat(DEFAULT.observe(s).postmortem()).as("the same threat is not reviewed twice").isNull();
    }
}
