package com.nodezero.sim.state;

/**
 * A counter the player applied. Between {@code appliedTick} and {@code activeTick} it is being built
 * (transitional side effects such as downtime or a write lock); from {@code activeTick} on it works.
 * Removing it (slice 7c, decision AI) takes as long as building it and hurts the same way; it keeps
 * working until {@code removedTick}, then it is gone.
 *
 * @param option chosen option id, or {@code null} for counters without options
 * @param removedTick tick the removal completes, or -1 if it is not being removed
 * @param setting the player's value for the setting that drives the counter's effect, or {@code null} for the default
 */
public record InstalledCounter(String id, String option, long appliedTick, long activeTick, long removedTick,
        Double setting) {

    public InstalledCounter(String id, String option, long appliedTick, long activeTick) {
        this(id, option, appliedTick, activeTick, -1, null);
    }

    public boolean isActive(long tick) {
        return tick >= activeTick;
    }

    public boolean isRemoving() {
        return removedTick >= 0;
    }

    /** Building or being removed: the transitional side effects apply. */
    public boolean inTransition(long tick) {
        return !isActive(tick) || isRemoving();
    }

    public InstalledCounter removedAt(long tick) {
        return new InstalledCounter(id, option, appliedTick, activeTick, tick, setting);
    }

    public InstalledCounter withSetting(double value) {
        return new InstalledCounter(id, option, appliedTick, activeTick, removedTick, value);
    }
}
