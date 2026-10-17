package com.nodezero.sim.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Slice 1c — the user implements {@link FillModel}; these tests are the spec.
 * Units are MB. Default disk: 100 MB. Default load: 10 rps x 1 MB in, 4 MB/s drained -> net +6 MB/s.
 */
class FillModelTest {

    private static final double DT = 0.1;
    private static final Offset<Double> EPS = within(1e-9);

    private final FillModel disk = new FillModel(100);
    private final FlowLoad netPlusSix = new FlowLoad(10, 1, 4);

    private static FillState at(double level, double capacity) {
        return new FillState(level, level / capacity, level >= capacity, 0, Double.POSITIVE_INFINITY);
    }

    @Nested
    class Accumulation {

        @Test
        void levelRisesByNetInflow() {
            FillState s = disk.step(FillState.empty(), netPlusSix, DT);

            // (10 * 1 - 4) MB/s * 0.1 s
            assertThat(s.level()).isCloseTo(0.6, EPS);
            assertThat(s.fillRatio()).isCloseTo(0.006, EPS);
            assertThat(s.full()).isFalse();
            assertThat(s.droppedPerSecond()).isZero();
        }

        @Test
        void drainsWhenInflowStops() {
            FillState s = disk.step(at(50, 100), new FlowLoad(0, 1, 4), DT);

            assertThat(s.level()).isCloseTo(49.6, EPS);
        }

        @Test
        void levelNeverDropsBelowZero() {
            FillState s = disk.step(FillState.empty(), new FlowLoad(0, 1, 4), DT);

            assertThat(s.level()).isZero();
        }

        @Test
        void accumulatesOverManyTicks() {
            FillState s = FillState.empty();
            for (int i = 0; i < 100; i++) {
                s = disk.step(s, netPlusSix, DT);
            }

            // 6 MB/s for 10 s
            assertThat(s.level()).isCloseTo(60.0, within(1e-6));
        }
    }

    @Nested
    class Capacity {

        @Test
        void reachingCapacityMarksFullAndDropsTheOverflow() {
            FillState s = disk.step(at(99.8, 100), netPlusSix, DT);

            // only 0.2 MB of the 0.6 MB fits; 0.4 MB over 0.1 s = 4 MB/s of failed writes
            assertThat(s.level()).isCloseTo(100.0, EPS);
            assertThat(s.full()).isTrue();
            assertThat(s.droppedPerSecond()).isCloseTo(4.0, EPS);
        }

        @Test
        void fullDiskDropsAllNetInflow() {
            FillState s = disk.step(at(100, 100), netPlusSix, DT);

            assertThat(s.droppedPerSecond()).isCloseTo(6.0, EPS);
        }

        @Test
        void recoversOnceSpaceIsFreed() {
            // e.g. logrotate kicked in: drain now exceeds inflow
            FillState s = disk.step(at(100, 100), new FlowLoad(10, 0.1, 4), DT);

            assertThat(s.level()).isCloseTo(99.7, EPS);
            assertThat(s.full()).isFalse();
            assertThat(s.droppedPerSecond()).isZero();
        }
    }

    @Nested
    class Prediction {

        @Test
        void predictsTimeUntilFullLikePredictLinear() {
            // Wave 2 log storm: 200 rps x 2 KB DEBUG logs = 0.4 MB/s on a 1000 MB disk
            FillModel bigDisk = new FillModel(1000);

            FillState s = bigDisk.step(FillState.empty(), new FlowLoad(200, 0.002, 0), DT);

            // (1000 - 0.04) MB / 0.4 MB/s
            assertThat(s.secondsToFull()).isCloseTo(2499.9, within(1e-6));
        }

        @Test
        void biggerDiskOnlyPostponesTheProblem() {
            FlowLoad logStorm = new FlowLoad(200, 0.002, 0);

            double small = new FillModel(1000).step(FillState.empty(), logStorm, DT).secondsToFull();
            double big = new FillModel(2000).step(FillState.empty(), logStorm, DT).secondsToFull();

            assertThat(big).isFinite().isGreaterThan(small);
        }

        @Test
        void noPredictionWhenLevelIsNotRising() {
            FillState flat = disk.step(at(50, 100), new FlowLoad(4, 1, 4), DT);
            FillState falling = disk.step(at(50, 100), new FlowLoad(0, 1, 4), DT);

            assertThat(flat.secondsToFull()).isEqualTo(Double.POSITIVE_INFINITY);
            assertThat(falling.secondsToFull()).isEqualTo(Double.POSITIVE_INFINITY);
        }

        @Test
        void fullAndRisingMeansNoTimeLeft() {
            FillState s = disk.step(at(100, 100), netPlusSix, DT);

            assertThat(s.secondsToFull()).isZero();
        }
    }

    @Nested
    class Validation {

        @Test
        void rejectsInvalidCapacity() {
            assertThatThrownBy(() -> new FillModel(0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new FillModel(-1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new FillModel(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new FillModel(Double.POSITIVE_INFINITY))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsNonPositiveDt() {
            assertThatThrownBy(() -> disk.step(FillState.empty(), netPlusSix, 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> disk.step(FillState.empty(), netPlusSix, Double.NaN))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsInvalidFlowLoad() {
            assertThatThrownBy(() -> new FlowLoad(10, -1, 0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new FlowLoad(10, 1, -1)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
