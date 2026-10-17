package com.nodezero.sim.state;

/**
 * Which wave is running and since when. Wave 0 is the preparation phase before the first wave.
 *
 * @param number current wave; matches {@code trigger.wave} of the threat it brings
 * @param startTick tick at which the wave started; time into the wave is {@code (tick - startTick) * DT}
 */
public record WaveState(int number, long startTick) {

    public static final WaveState PREPARATION = new WaveState(0, 0);

    public WaveState {
        if (number < 0) {
            throw new IllegalArgumentException("wave number must be >= 0: " + number);
        }
        if (startTick < 0) {
            throw new IllegalArgumentException("startTick must be >= 0: " + startTick);
        }
    }
}
