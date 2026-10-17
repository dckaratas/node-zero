package com.nodezero.sim.model;

/**
 * Fixed-size slot pool where each request holds a slot for the load's hold time (Tomcat threads,
 * HikariCP connections). Mathematically an M/M/c queue with {@code mu = 1 / holdTime}; what differs
 * from {@link UtilizationModel} is that a held slot may be idle-waiting (e.g. on the database), so a
 * saturated pool does not imply a busy CPU.
 *
 * <p>{@code pending} combines the Erlang-C mean queue length with the overflow backlog and is capped
 * at the queue limit (an approximation of a finite M/M/c/K queue, ADR-0013).
 *
 * @param poolSize number of slots (c), e.g. HikariCP maximumPoolSize
 * @param queueLimit waiting requests above which arrivals are rejected
 */
public record OccupancyModel(int poolSize, int queueLimit) implements ResourceModel<OccupancyState, HoldLoad> {

    public OccupancyModel {
        if (poolSize <= 0) {
            throw new IllegalArgumentException("poolSize must be > 0: " + poolSize);
        }
        if (queueLimit < 0) {
            throw new IllegalArgumentException("queueLimit must be >= 0: " + queueLimit);
        }
    }

    @Override
    public HoldLoad loadFor(Demand demand) {
        return new HoldLoad(demand.arrivalRps(), demand.holdTimeMs(), demand.reservedSlots());
    }

    /** Slots left for requests after background reservations; at least one. */
    public int availableSlots(int reservedSlots) {
        return Math.max(1, poolSize - reservedSlots);
    }

    @Override
    public OccupancyState step(OccupancyState previous, HoldLoad load, double dt) {
        double lambda = load.arrivalRps();
        int slots = availableSlots(load.reservedSlots());
        double releaseRate = 1000.0 / load.holdTimeMs(); // mu: requests one slot completes per second
        double capacity = slots * releaseRate;
        Backlog backlog = Backlog.next(previous.backlog(), lambda, capacity, queueLimit, dt);

        // Little's law: busy slots = arrival rate * hold time. Queued work keeps every slot busy.
        // Reserved slots count as active, as they do in hikaricp_connections_active.
        double demand = lambda * load.holdTimeMs() / 1000.0;
        double active = (backlog.size() > 0 ? slots : Math.min(demand, slots)) + (poolSize - slots);

        double steadyWaitMs = ErlangC.cappedMeanWaitMs(slots, releaseRate, lambda);
        // Little's law again, on the waiting line: Lq = lambda * Wq.
        double steadyPending = Math.min(lambda, ErlangC.RHO_CAP * capacity) * steadyWaitMs / 1000.0;
        double pending = Math.min(steadyPending + backlog.size(), queueLimit);
        double waitMs = steadyWaitMs + 1000.0 * backlog.size() / capacity;

        return new OccupancyState(active, pending, waitMs, backlog.rejectedRps(), backlog.size());
    }
}
