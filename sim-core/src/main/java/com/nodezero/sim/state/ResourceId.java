package com.nodezero.sim.state;

/**
 * Station resources. Declaration order is the deterministic iteration order (EnumMap);
 * append new values at the end so existing replays keep their order.
 */
public enum ResourceId {
    CPU,
    THREADS,
    DB_POOL,
    DISK,
    DB_CPU,
    DB_IO
}
