package com.nodezero.sim.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.nodezero.sim.content.ContentLoader;
import com.nodezero.sim.engine.Command.StartWave;
import com.nodezero.sim.model.FillState;
import com.nodezero.sim.model.OccupancyState;
import com.nodezero.sim.model.UtilizationState;
import com.nodezero.sim.state.GameState;
import com.nodezero.sim.state.ResourceId;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Chapter 1 waves played through the request pipeline on NODE-01. */
class GameTest {

    private static Game game;

    @BeforeAll
    static void setUp() {
        // Practice balance: waves play out fully, so the tests can observe the physics of a whole wave.
        game = Game.chapterOne(GameBalance.PRACTICE);
    }

    /** Starts {@code wave} at tick 1 and runs until {@code untilTick}. */
    private static GameState play(int wave, long untilTick) {
        GameState s = game.newState(42);
        for (long tick = 1; tick <= untilTick; tick++) {
            s = game.step(s, tick == 1 && wave > 0 ? List.of(new StartWave(wave)) : List.of(), tick);
        }
        return s;
    }

    private static UtilizationState util(GameState s, ResourceId id) {
        return (UtilizationState) s.resource(id).state();
    }

    private static OccupancyState pool(GameState s, ResourceId id) {
        return (OccupancyState) s.resource(id).state();
    }

    @Test
    void quietStationIsHealthy() {
        GameState s = play(0, 100);

        assertThat(util(s, ResourceId.CPU).rho()).isCloseTo(0.5, within(1e-9));
        assertThat(pool(s, ResourceId.THREADS).active()).isLessThan(10);
        assertThat(pool(s, ResourceId.DB_POOL).pending()).isLessThan(0.1);
        assertThat(pool(s, ResourceId.THREADS).rejectedRps()).isZero();
    }

    @Test
    void startWaveRecordsWaveAndStartTick() {
        GameState s = play(3, 5);

        assertThat(s.wave().number()).isEqualTo(3);
        assertThat(s.wave().startTick()).isEqualTo(1);
    }

    @Test
    void festivalPinsTheReactorAndTomcatTurnsTheExcessAway() {
        // 50 s into wave 3: plateau at 300 rps against 200 rps of CPU
        GameState s = play(3, 501);

        // Tomcat's accept queue is full, so the excess never reaches the CPU: it is pinned at 100 %.
        assertThat(util(s, ResourceId.CPU).rho()).isCloseTo(1.0, within(0.01));
        assertThat(util(s, ResourceId.CPU).latencyMs()).isGreaterThan(500);
        assertThat(pool(s, ResourceId.THREADS).rejectedRps()).isCloseTo(100, within(1.0));
    }

    @Test
    void slowQuerySaturatesPoolsWhileAppCpuLooksIdle() {
        // Wave 4 after 100 s: orders grew to 1.4M rows and the unindexed query outgrew the database.
        GameState s = play(4, 1001);

        assertThat(pool(s, ResourceId.DB_POOL).active()).isCloseTo(10.0, within(1e-9));
        assertThat(pool(s, ResourceId.DB_POOL).pending()).isGreaterThan(100);
        assertThat(pool(s, ResourceId.THREADS).active()).isCloseTo(200.0, within(1e-9));
        assertThat(pool(s, ResourceId.THREADS).rejectedRps()).isGreaterThan(0);
        // The deceptive signal: threads wait on the database, so the application CPU is mostly idle.
        assertThat(util(s, ResourceId.CPU).rho()).isLessThan(0.5);
    }

    @Test
    void slowQueryGetsWorseAsTheTableGrows() {
        double early = pool(play(4, 801), ResourceId.THREADS).rejectedRps();
        double late = pool(play(4, 1201), ResourceId.THREADS).rejectedRps();

        assertThat(late).isGreaterThan(early);
    }

    @Test
    void debugLogsFillTheCargoHold() {
        // Wave 2: 100 rps x 2 KB = 0.2 MB/s on a disk with 25 MB free -> about 125 s to full
        GameState s = play(2, 601);

        FillState disk = (FillState) s.resource(ResourceId.DISK).state();
        assertThat(disk.level()).isCloseTo(75 + 0.2 * 60, within(0.5));
        assertThat(disk.secondsToFull()).isBetween(60.0, 70.0);
    }

    @Test
    void sameInputsReplayIdentically() {
        assertThat(play(4, 300)).isEqualTo(play(4, 300));
    }

    @Test
    void rulesRejectAStationMissingAPipelineResource() {
        EnumMap<ResourceId, com.nodezero.sim.model.ResourceModel<?, ?>> models =
                new EnumMap<>(StationLayout.node01().models());
        models.remove(ResourceId.DISK);
        StationLayout noDisk = new StationLayout(models, 100, 0.2, 0.0002, 0);

        assertThatThrownBy(() -> new Rules(noDisk, ContentLoader.loadDefault()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("station needs DISK as a FILL model");
    }
}
