package com.nodezero.sim.state;

/**
 * The latest run of an incident action (slice 7b).
 *
 * @param option chosen option id, or {@code null}
 * @param startTick tick the action ran; its windowed effects are timed from here
 * @param endTick first tick after its last windowed effect (equals {@code startTick} for an instant action)
 * @param readyTick first tick it may run again (cooldown)
 */
public record ActionRun(String id, String option, long startTick, long endTick, long readyTick) {}
