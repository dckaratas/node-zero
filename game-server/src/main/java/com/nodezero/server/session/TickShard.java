package com.nodezero.server.session;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One scheduler thread ticking its share of the sessions every 100 ms (decision L).
 * Sessions are added and removed rarely, so a copy-on-write list keeps the per-tick loop lock-free.
 */
final class TickShard implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(TickShard.class);
    private static final long TICK_BUDGET_NANOS = TimeUnit.MILLISECONDS.toNanos(100);

    private final int index;
    private final List<Session> sessions = new CopyOnWriteArrayList<>();
    private final SessionHost host;
    private final Timer loopTimer;
    private final Counter overruns;

    TickShard(int index, SessionHost host, MeterRegistry registry) {
        this.index = index;
        this.host = host;
        String shard = Integer.toString(index);
        this.loopTimer = Timer.builder("nodezero.tick.loop")
                .description("Time to tick every session of a shard once")
                .tag("shard", shard)
                .publishPercentiles(0.5, 0.99)
                .register(registry);
        this.overruns = Counter.builder("nodezero.tick.overruns")
                .description("Shard loops that took longer than one tick (100 ms)")
                .tag("shard", shard)
                .register(registry);
    }

    void add(Session session) {
        sessions.add(session);
    }

    void remove(Session session) {
        sessions.remove(session);
    }

    int size() {
        return sessions.size();
    }

    @Override
    public void run() {
        long start = System.nanoTime();
        for (Session session : sessions) {
            try {
                session.tick();
            } catch (RuntimeException e) {
                LOG.error("session {} failed on shard {}", session.id(), index, e);
                host.remove(session.id());
                session.fail(e);
            }
        }
        long elapsed = System.nanoTime() - start;
        loopTimer.record(elapsed, TimeUnit.NANOSECONDS);
        if (elapsed > TICK_BUDGET_NANOS) {
            overruns.increment();
            LOG.warn("shard {} loop took {} ms for {} sessions", index, elapsed / 1_000_000, sessions.size());
        }
    }
}
