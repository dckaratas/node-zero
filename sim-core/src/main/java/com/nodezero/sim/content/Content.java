package com.nodezero.sim.content;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** All validated game content, keyed by id in index order. */
public record Content(Map<String, ThreatDef> threats, Map<String, CounterDef> counters, Messages messages) {

    public Content {
        threats = Collections.unmodifiableMap(new LinkedHashMap<>(threats));
        counters = Collections.unmodifiableMap(new LinkedHashMap<>(counters));
    }

    public ThreatDef threat(String id) {
        return lookup(threats, id, "threat");
    }

    public CounterDef counter(String id) {
        return lookup(counters, id, "counter");
    }

    private static <T> T lookup(Map<String, T> byId, String id, String kind) {
        T value = byId.get(id);
        if (value == null) {
            throw new IllegalArgumentException("unknown " + kind + ": " + id);
        }
        return value;
    }
}
