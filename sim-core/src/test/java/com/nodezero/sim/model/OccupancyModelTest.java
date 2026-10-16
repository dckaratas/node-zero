package com.nodezero.sim.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * NODE-01 HikariCP: maximumPoolSize = 10, wait queue limit = 100.
 * A healthy query holds a connection for 50 ms, so the pool serves at most 10 / 0.05 = 200 rps.
 * Expected waits are Erlang-C with c = 10, mu = 1 / holdTime.
 */
class OccupancyModelTest {

    private static final double DT = 0.1;
    private static final Offset<Double> EPS = within(1e-6);

    private final OccupancyModel hikari = new OccupancyModel(10, 100);

    private OccupancyState run(double rps, double holdMs, int ticks) {
        OccupancyState s = OccupancyState.initial();
        for (int i = 0; i < ticks; i++) {
            s = hikari.step(s, new HoldLoad(rps, holdMs), DT);
        }
        return s;
    }

    @Test
    void activeConnectionsFollowLittlesLaw() {
        OccupancyState s = run(100, 50, 1);

        // L = lambda * W = 100 rps * 0.05 s
        assertThat(s.active()).isCloseTo(5.0, EPS);
        assertThat(s.waitMs()).isCloseTo(0.3610536, within(1e-6));
        assertThat(s.rejectedRps()).isZero();
    }

    @Test
    void requestsStartWaitingBeforeThePoolIsFull() {
        OccupancyState s = run(160, 50, 1);

        // 8 of 10 connections busy, yet requests already queue: Erlang-C(c=10, a=8) = 0.40918
        assertThat(s.active()).isCloseTo(8.0, EPS);
        assertThat(s.pending()).isCloseTo(1.6367206, within(1e-6));
        assertThat(s.waitMs()).isCloseTo(10.2295038, within(1e-6));
    }

    @Test
    void slowQueryExhaustsThePool() {
        // Missing index: 500 ms per query -> capacity drops to 10 / 0.5 = 20 rps
        OccupancyState s = run(100, 500, 1);

        assertThat(s.active()).isCloseTo(10.0, EPS);
        // backlog grows by (100 - 20) rps * 0.1 s
        assertThat(s.backlog()).isCloseTo(8.0, EPS);
        // steady-state wait at rho cap (4818.69 ms) + backlog drain 8 / 20 rps
        assertThat(s.waitMs()).isCloseTo(4818.6921019 + 400.0, within(1e-5));
    }

    @Test
    void pendingNeverExceedsQueueLimit() {
        OccupancyState s = run(100, 500, 1);

        // Erlang Lq at the cap (~95.4) + backlog 8 would be ~103.4; the queue holds at most 100.
        assertThat(s.pending()).isCloseTo(100.0, EPS);
    }

    @Test
    void fullQueueRejectsTheExcess() {
        // backlog +8 per tick reaches the limit of 100 after 12.5 ticks
        OccupancyState s = run(100, 500, 14);

        assertThat(s.backlog()).isCloseTo(100.0, EPS);
        assertThat(s.rejectedRps()).isCloseTo(80.0, EPS);
    }

    @Test
    void activeNeverExceedsPoolSize() {
        for (int rps = 0; rps <= 400; rps += 10) {
            assertThat(run(rps, 50, 3).active()).as("rps=%d", rps).isLessThanOrEqualTo(10.0);
        }
    }

    @Test
    void poolIsBusyWhileBacklogDrains() {
        OccupancyState saturated = run(100, 500, 5);

        OccupancyState draining = hikari.step(saturated, new HoldLoad(5, 500), DT);

        // demand alone would be 5 * 0.5 = 2.5 connections, but queued work keeps all 10 busy
        assertThat(draining.backlog()).isGreaterThan(0);
        assertThat(draining.active()).isCloseTo(10.0, EPS);
    }

    @Test
    void sameQueueMathAsUtilizationModel() {
        // 4 slots held 20 ms each is the same M/M/4 queue as 4 cores at 50 rps
        OccupancyState pool = new OccupancyModel(4, 100).step(OccupancyState.initial(), new HoldLoad(100, 20), DT);
        UtilizationState cpu = new UtilizationModel(4, 50, 100).step(UtilizationState.initial(), new RateLoad(100), DT);

        assertThat(20 + pool.waitMs()).isCloseTo(cpu.latencyMs(), EPS);
    }

    @Test
    void rejectsInvalidParameters() {
        assertThatThrownBy(() -> new OccupancyModel(0, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OccupancyModel(10, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HoldLoad(100, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HoldLoad(100, Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }
}
