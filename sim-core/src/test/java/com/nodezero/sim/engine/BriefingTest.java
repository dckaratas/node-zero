package com.nodezero.sim.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.nodezero.sim.engine.Command.AnswerPostmortem;
import com.nodezero.sim.engine.Command.StartWave;
import com.nodezero.sim.state.GameState;
import com.nodezero.sim.state.WaveState;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The next-wave briefing (slice 9c, ADR-0018 CA/CB): the event and the comms, never the root cause. */
class BriefingTest {

    private static final Game GAME = Game.chapterOne(GameBalance.PRACTICE);

    private static GameState playWave(GameState s, int wave) {
        s = GAME.step(s, List.of(new StartWave(wave)), s.tick() + 1);
        while (s.wave().phase() == WaveState.Phase.RUNNING) {
            s = GAME.step(s, List.of(), s.tick() + 1);
        }
        return s;
    }

    @Test
    void theBriefingNamesTheNextEventAndCarriesTheFestivalTeaserAWaveAhead() {
        GameState s = GAME.newState(1);
        assertThat(GAME.observe(s).briefing()).isEqualTo(new Observation.Briefing(1, "briefing.quiet", List.of()));

        s = playWave(s, 1);
        assertThat(GAME.observe(s).briefing())
                .isEqualTo(new Observation.Briefing(2, "briefing.deploy", List.of("comms.festival_teaser")));

        GameState running = GAME.step(s, List.of(new StartWave(2)), s.tick() + 1);
        assertThat(GAME.observe(running).briefing().wave()).as("a running wave describes itself").isEqualTo(2);

        s = playWave(s, 2);
        s = GAME.step(s, List.of(new AnswerPostmortem("b")), s.tick() + 1);
        assertThat(GAME.observe(s).briefing()).isEqualTo(new Observation.Briefing(3, "briefing.campaign", List.of()));
    }

    @Test
    void noBriefingOnceTheChapterHasNoWaveLeft() {
        GameState s = GAME.newState(1);
        for (int w = 1; w <= GAME.rules().lastWave(); w++) {
            s = playWave(s, w);
            if (s.progress().postmortem() != null) {
                s = GAME.step(s, List.of(new AnswerPostmortem("a")), s.tick() + 1);
            }
        }
        assertThat(GAME.observe(s).briefing()).isNull();
    }
}
