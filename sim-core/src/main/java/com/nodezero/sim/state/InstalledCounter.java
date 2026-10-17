package com.nodezero.sim.state;

/**
 * A counter the player applied. Between {@code appliedTick} and {@code activeTick} it is being built
 * (transitional side effects such as downtime or a write lock); from {@code activeTick} on it works.
 *
 * @param option chosen option id, or {@code null} for counters without options
 */
public record InstalledCounter(String id, String option, long appliedTick, long activeTick) {

    public boolean isActive(long tick) {
        return tick >= activeTick;
    }
}
