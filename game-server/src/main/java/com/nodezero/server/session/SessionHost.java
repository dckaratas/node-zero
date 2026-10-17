package com.nodezero.server.session;

import com.nodezero.server.ServerProperties;
import com.nodezero.sim.engine.Game;
import com.nodezero.sim.engine.SimConstants;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Owns all sessions (in memory, decision K) and the shard threads that tick them (decision L).
 * Seeds come from {@link SecureRandom}: the server may be non-deterministic, the simulation may not.
 */
@Component
public final class SessionHost implements SmartLifecycle {

    /** Thrown when the server is at its session limit. */
    public static final class SessionLimitException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        SessionLimitException(int limit) {
            super("session limit reached: " + limit);
        }
    }

    private final Game game;
    private final ServerProperties properties;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, TickShard> shardOf = new ConcurrentHashMap<>();
    private final List<TickShard> shards = new ArrayList<>();
    private final AtomicInteger nextShard = new AtomicInteger();
    private final SecureRandom seeds = new SecureRandom();
    private final List<ScheduledExecutorService> executors = new ArrayList<>();
    private volatile boolean running;

    public SessionHost(Game game, ServerProperties properties, MeterRegistry registry) {
        this.game = game;
        this.properties = properties;
        for (int i = 0; i < properties.effectiveShards(); i++) {
            shards.add(new TickShard(i, this, registry));
        }
        Gauge.builder("nodezero.sessions", sessions, Map::size)
                .description("Open game sessions")
                .register(registry);
    }

    public Session open(SessionListener listener) {
        if (sessions.size() >= properties.maxSessions()) {
            throw new SessionLimitException(properties.maxSessions());
        }
        Session session = new Session(UUID.randomUUID().toString(), seeds.nextLong(), game,
                properties.snapshotEveryTicks(), listener);
        TickShard shard = shards.get(Math.floorMod(nextShard.getAndIncrement(), shards.size()));
        sessions.put(session.id(), session);
        shardOf.put(session.id(), shard);
        shard.add(session);
        return session;
    }

    public Optional<Session> find(String id) {
        return Optional.ofNullable(sessions.get(id));
    }

    public void remove(String id) {
        Session session = sessions.remove(id);
        TickShard shard = shardOf.remove(id);
        if (session != null && shard != null) {
            shard.remove(session);
        }
    }

    public int size() {
        return sessions.size();
    }

    @Override
    public void start() {
        long periodMillis = Math.round(1000.0 / SimConstants.TICK_HZ);
        for (TickShard shard : shards) {
            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "nodezero-tick-" + executors.size());
                t.setDaemon(true);
                return t;
            });
            executor.scheduleAtFixedRate(shard, periodMillis, periodMillis, TimeUnit.MILLISECONDS);
            executors.add(executor);
        }
        running = true;
    }

    @Override
    public void stop() {
        executors.forEach(ScheduledExecutorService::shutdownNow);
        executors.clear();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
