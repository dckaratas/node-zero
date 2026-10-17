package com.nodezero.server.protocol;

import com.nodezero.sim.engine.Observation;

/**
 * Turns one session's observations into the snapshots worth sending (ADR-0015 addendum, decision T).
 * <ul>
 *   <li>A view equal to the last one sent (ignoring the tick) is skipped: an idle station costs nothing.</li>
 *   <li>{@code insights} and {@code counters} are sent only when they changed since the last snapshot.</li>
 *   <li>Every {@code keyframeEveryTicks} a full snapshot goes out regardless, as a heartbeat and resync.</li>
 * </ul>
 * The socket is ordered and lossless (TCP, slow clients are disconnected, never skipped), so the
 * client can keep the last values it saw. One instance per connection; not thread-safe.
 */
public final class SnapshotStream {

    private final int keyframeEveryTicks;
    private Observation last;
    private long lastKeyframeTick;

    public SnapshotStream(int keyframeEveryTicks) {
        if (keyframeEveryTicks < 1) {
            throw new IllegalArgumentException("keyframeEveryTicks must be >= 1");
        }
        this.keyframeEveryTicks = keyframeEveryTicks;
    }

    /** The snapshot to send for this observation, or {@code null} when there is nothing new. */
    public ServerMessage.Snapshot next(Observation now) {
        boolean keyframe = last == null || now.tick() - lastKeyframeTick >= keyframeEveryTicks;
        if (!keyframe && sameView(last, now)) {
            return null;
        }
        boolean insights = keyframe || !now.insights().equals(last.insights());
        boolean counters = keyframe || !now.counters().equals(last.counters());
        if (keyframe) {
            lastKeyframeTick = now.tick();
        }
        last = now;
        return new ServerMessage.Snapshot(now.tick(), now.credits(), now.wave(), now.phase(), now.shield(),
                now.metrics(), now.signals(), insights ? now.insights() : null, counters ? now.counters() : null);
    }

    private static boolean sameView(Observation a, Observation b) {
        return a.credits() == b.credits()
                && a.wave() == b.wave()
                && a.phase() == b.phase()
                && Double.compare(a.shield(), b.shield()) == 0
                && a.metrics().equals(b.metrics())
                && a.signals().equals(b.signals())
                && a.insights().equals(b.insights())
                && a.counters().equals(b.counters());
    }
}
