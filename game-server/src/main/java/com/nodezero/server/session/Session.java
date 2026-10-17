package com.nodezero.server.session;

import com.nodezero.sim.engine.Command;
import com.nodezero.sim.engine.Game;
import com.nodezero.sim.state.GameState;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * One player's game. Inputs arrive from WebSocket threads through a lock-free inbox; {@link #tick()}
 * runs only on the session's shard thread, so the game state itself needs no locking.
 *
 * <p>Commands are validated by trial: {@link Game#step} is pure, so stepping with a candidate command
 * and throwing the result away has no side effects. The last successful trial is the next state.
 */
public final class Session {

    /** A command as accepted at a tick; seed + this log replays the session exactly (ADR-0006). */
    public record LoggedCommand(long tick, Command command) {}

    private sealed interface Inbound {
        record Sim(Command command) implements Inbound {}

        record Control(boolean pause) implements Inbound {}
    }

    private final String id;
    private final long seed;
    private final Game game;
    private final SessionListener listener;
    private final int snapshotEveryTicks;
    private final ConcurrentLinkedQueue<Inbound> inbox = new ConcurrentLinkedQueue<>();
    private final List<Command> held = new ArrayList<>();
    private final List<LoggedCommand> log = new ArrayList<>();

    private volatile GameState state;
    private boolean paused;

    public Session(String id, long seed, Game game, SessionListener listener) {
        this(id, seed, game, 1, listener);
    }

    /** @param snapshotEveryTicks observe (and report) only on ticks divisible by this; 1 = every tick */
    public Session(String id, long seed, Game game, int snapshotEveryTicks, SessionListener listener) {
        this.id = id;
        this.seed = seed;
        this.game = game;
        this.listener = listener;
        this.snapshotEveryTicks = snapshotEveryTicks;
        this.state = game.newState(seed);
    }

    public String id() {
        return id;
    }

    public long seed() {
        return seed;
    }

    /** Latest state; safe to read from any thread (published through a volatile field). */
    public GameState state() {
        return state;
    }

    public void submit(Command command) {
        inbox.add(new Inbound.Sim(command));
    }

    public void pause() {
        inbox.add(new Inbound.Control(true));
    }

    public void resume() {
        inbox.add(new Inbound.Control(false));
    }

    /** Accepted commands so far, copied for another thread. */
    public List<LoggedCommand> log() {
        synchronized (log) {
            return List.copyOf(log);
        }
    }

    /** Advances one tick (10 Hz) and reports the view on snapshot ticks. Shard thread only. */
    void tick() {
        Inbound in;
        while ((in = inbox.poll()) != null) {
            switch (in) {
                case Inbound.Sim sim -> held.add(sim.command());
                case Inbound.Control control -> {
                    if (control.pause() != paused) {
                        paused = control.pause();
                        listener.onPauseChanged(paused);
                    }
                }
            }
        }
        if (paused) {
            return; // active pause: commands wait in `held` until resume (ADR-0001)
        }

        GameState current = state;
        long next = current.tick() + 1;
        List<Command> accepted = new ArrayList<>(held.size());
        GameState result = null;
        for (Command command : held) {
            List<Command> candidate = new ArrayList<>(accepted);
            candidate.add(command);
            try {
                result = game.step(current, candidate, next);
                accepted.add(command);
            } catch (IllegalArgumentException rejected) {
                listener.onRejected(command, rejected.getMessage());
            }
        }
        held.clear();
        if (accepted.isEmpty()) {
            result = game.step(current, List.of(), next);
        } else {
            synchronized (log) {
                accepted.forEach(c -> log.add(new LoggedCommand(next, c)));
            }
        }
        state = result;
        if (next % snapshotEveryTicks == 0) {
            listener.onObservation(game.observe(result));
        }
    }

    void fail(Throwable error) {
        listener.onFailure(error);
    }
}
