package com.nodezero.sim.state;

/**
 * A threat whose costs currently apply. Persistent threats (DEBUG logs, a growing table) stay
 * active after their wave until a counter neutralises them: technical debt carried forward.
 *
 * @param sinceTick tick the threat arrived; per-row costs and jobs are timed from here
 */
public record ActiveThreat(String id, long sinceTick) {}
