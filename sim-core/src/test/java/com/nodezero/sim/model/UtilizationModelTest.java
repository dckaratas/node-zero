package com.nodezero.sim.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * NODE-01 CPU: c = 4 cores, mu = 50 rps per core (S = 20 ms), capacity 200 rps, queue limit 100.
 * Expected latencies are Erlang-C (M/M/c) values, cross-checked with the closed-form sum formula.
 */
class UtilizationModelTest {

    private static final double DT = 0.1;
    private static final Offset<Double> EPS = within(1e-6);

    private final UtilizationModel cpu = new UtilizationModel(4, 50, 100);

    private UtilizationState stepFromIdle(double rps) {
        return cpu.step(UtilizationState.initial(), new RateLoad(rps), DT);
    }

    private UtilizationState run(UtilizationState start, double rps, int ticks) {
        UtilizationState s = start;
        for (int i = 0; i < ticks; i++) {
            s = cpu.step(s, new RateLoad(rps), DT);
        }
        return s;
    }

    @Nested
    class SteadyState {

        @Test
        void halfLoad() {
            UtilizationState s = stepFromIdle(100);

            assertThat(s.rho()).isCloseTo(0.5, EPS);
            // a = 2, Erlang-C = 4/23 -> W = 20 + 40/23 = 500/23 ms
            assertThat(s.latencyMs()).isCloseTo(500.0 / 23, EPS);
            assertThat(s.queue()).isZero();
            assertThat(s.rejectedRps()).isZero();
        }

        @Test
        void heavyLoad() {
            UtilizationState s = stepFromIdle(180);

            assertThat(s.rho()).isCloseTo(0.9, EPS);
            // a = 3.6, Erlang-C = 69.984 / 88.84 -> W = 20 + 0.787753 / 20 s
            assertThat(s.latencyMs()).isCloseTo(59.3876632, within(1e-5));
        }

        @Test
        void latencyGrowsNonLinearlyButPoolingSoftensIt() {
            double ratio = stepFromIdle(180).latencyMs() / stepFromIdle(100).latencyMs();

            // Single server (M/M/1) would give 5x; four pooled cores give ~2.7x.
            assertThat(ratio).isCloseTo(2.7318, within(1e-3));
        }

        @Test
        void idleServerRespondsInServiceTime() {
            UtilizationState s = stepFromIdle(0);

            assertThat(s.rho()).isZero();
            assertThat(s.latencyMs()).isCloseTo(20.0, EPS);
        }

        @Test
        void singleServerReducesToMm1() {
            UtilizationModel single = new UtilizationModel(1, 50, 100);

            UtilizationState s = single.step(UtilizationState.initial(), new RateLoad(25), DT);

            // M/M/1: W = S / (1 - rho) = 20 / 0.5
            assertThat(s.latencyMs()).isCloseTo(40.0, EPS);
        }

        @Test
        void latencyNeverDecreasesAsLoadRises() {
            double previous = 0;
            for (int rps = 0; rps <= 300; rps++) {
                double latency = stepFromIdle(rps).latencyMs();
                assertThat(latency).as("rps=%d", rps).isGreaterThanOrEqualTo(previous);
                previous = latency;
            }
        }
    }

    @Nested
    class Saturation {

        @Test
        void fullUtilizationStaysFinite() {
            UtilizationState s = stepFromIdle(200);

            assertThat(s.rho()).isCloseTo(1.0, EPS);
            assertThat(s.latencyMs()).isFinite();
            // Capped at rho = 0.99 (ADR-0013); queue does not grow at exactly lambda = c*mu.
            assertThat(s.latencyMs()).isCloseTo(508.9525880, within(1e-5));
            assertThat(s.queue()).isZero();
        }

        @Test
        void overloadGrowsQueueByExcessRate() {
            UtilizationState s = stepFromIdle(300);

            assertThat(s.rho()).isCloseTo(1.5, EPS);
            // (300 - 200) rps * 0.1 s
            assertThat(s.queue()).isCloseTo(10.0, EPS);
            assertThat(s.rejectedRps()).isZero();
        }

        @Test
        void queueFillsUpToLimitAfterTenTicks() {
            UtilizationState s = run(UtilizationState.initial(), 300, 10);

            assertThat(s.queue()).isCloseTo(100.0, EPS);
            assertThat(s.rejectedRps()).isCloseTo(0.0, EPS);
        }

        @Test
        void fullQueueRejectsTheExcess() {
            UtilizationState s = run(UtilizationState.initial(), 300, 11);

            assertThat(s.queue()).isCloseTo(100.0, EPS);
            assertThat(s.rejectedRps()).isCloseTo(100.0, EPS);
        }

        @Test
        void backlogAddsDrainTimeToLatency() {
            UtilizationState full = run(UtilizationState.initial(), 300, 11);

            // W(0.99) + 100 queued / 200 rps = 508.95 + 500 ms
            assertThat(full.latencyMs()).isCloseTo(508.9525880 + 500.0, within(1e-5));
        }

        @Test
        void queueDrainsWhenLoadDropsAndNeverGoesNegative() {
            UtilizationState full = new UtilizationState(100, 1.5, 0, 0);

            UtilizationState afterOne = run(full, 100, 1);
            UtilizationState afterTen = run(full, 100, 10);
            UtilizationState afterTwenty = run(full, 100, 20);

            // drains at (200 - 100) rps * 0.1 s = 10 per tick
            assertThat(afterOne.queue()).isCloseTo(90.0, EPS);
            assertThat(afterTen.queue()).isCloseTo(0.0, EPS);
            assertThat(afterTwenty.queue()).isZero();
        }
    }

    @Nested
    class Validation {

        @Test
        void rejectsInvalidParameters() {
            assertThatThrownBy(() -> new UtilizationModel(0, 50, 100)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new UtilizationModel(4, 0, 100)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new UtilizationModel(4, Double.NaN, 100))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new UtilizationModel(4, 50, -1)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsInvalidLoad() {
            assertThatThrownBy(() -> new RateLoad(-1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new RateLoad(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new RateLoad(Double.POSITIVE_INFINITY))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsNonPositiveDt() {
            assertThatThrownBy(() -> cpu.step(UtilizationState.initial(), new RateLoad(100), 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
