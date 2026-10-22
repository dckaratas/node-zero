package com.nodezero.server.protocol;

import com.nodezero.sim.engine.Observation;
import java.util.Objects;

/**
 * Turns one session's observations into the snapshots worth sending (ADR-0015 addendum, decision T).
 * <ul>
 *   <li>A view equal to the last one sent (ignoring the tick) is skipped: an idle station costs nothing.</li>
 *   <li>{@code insights}, {@code counters}, {@code codex}, {@code results}, {@code actions} and {@code alerts}
 *       are sent only when they changed since the last snapshot.</li>
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
        boolean codex = keyframe || !now.codex().equals(last.codex());
        boolean results = keyframe || !now.results().equals(last.results());
        boolean actions = keyframe || !now.actions().equals(last.actions());
        boolean alerts = keyframe || !now.alerts().equals(last.alerts());
        if (keyframe) {
            lastKeyframeTick = now.tick();
        }
        last = now;
        return new ServerMessage.Snapshot(now.tick(), now.credits(), now.wave(), now.phase(), now.shield(),
                now.burnRate(), now.shieldEmptyInS(), now.metrics(), now.signals(),
                insights ? now.insights() : null, counters ? now.counters() : null,
                now.postmortem(), codex ? now.codex() : null, results ? now.results() : null, now.chapter(),
                now.crew(), actions ? now.actions() : null, alerts ? now.alerts() : null);
    }

    private static boolean sameView(Observation a, Observation b) {
        return a.credits() == b.credits()
                && a.wave() == b.wave()
                && a.phase() == b.phase()
                && Double.compare(a.shield(), b.shield()) == 0
                && Double.compare(a.burnRate(), b.burnRate()) == 0
                && Double.compare(a.shieldEmptyInS(), b.shieldEmptyInS()) == 0
                && a.metrics().equals(b.metrics())
                && a.signals().equals(b.signals())
                && a.insights().equals(b.insights())
                && a.counters().equals(b.counters())
                && Objects.equals(a.postmortem(), b.postmortem())
                && a.codex().equals(b.codex())
                && a.results().equals(b.results())
                && Objects.equals(a.chapter(), b.chapter())
                && a.crew() == b.crew()
                && a.actions().equals(b.actions())
                && a.alerts().equals(b.alerts());
    }
}
