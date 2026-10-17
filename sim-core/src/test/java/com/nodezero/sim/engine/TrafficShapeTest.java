package com.nodezero.sim.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.nodezero.sim.content.ContentLoader;
import com.nodezero.sim.content.ThreatDef;
import org.junit.jupiter.api.Test;

/** Colony Festival: 100 rps baseline, ramp 20 s to x3, plateau 60 s, decay 40 s back to x1. */
class TrafficShapeTest {

    private final ThreatDef.Traffic festival = ContentLoader.loadDefault().threat("traffic-surge").traffic();

    @Test
    void followsRampPlateauAndDecay() {
        assertThat(TrafficShape.rateAt(festival, 0)).isCloseTo(100, within(1e-9));
        assertThat(TrafficShape.rateAt(festival, 10)).isCloseTo(200, within(1e-9));
        assertThat(TrafficShape.rateAt(festival, 50)).isCloseTo(300, within(1e-9));
        assertThat(TrafficShape.rateAt(festival, 100)).isCloseTo(200, within(1e-9));
    }

    @Test
    void settlesAtTheFinalMultiplierAfterTheShapeEnds() {
        assertThat(TrafficShape.rateAt(festival, 500)).isCloseTo(100, within(1e-9));
    }
}
