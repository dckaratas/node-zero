package com.nodezero.sim.content;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Translated texts by key, e.g. {@code pm.traffic_surge.q}. sim-core only hands out keys (ADR-0009). */
public record Messages(Map<String, String> en, Map<String, String> tr) {

    public Messages {
        en = Collections.unmodifiableMap(new LinkedHashMap<>(en));
        tr = Collections.unmodifiableMap(new LinkedHashMap<>(tr));
    }

    public Map<String, String> in(Language language) {
        return switch (language) {
            case EN -> en;
            case TR -> tr;
        };
    }

    public String text(Language language, String key) {
        String text = in(language).get(key);
        if (text == null) {
            throw new IllegalArgumentException("no " + language + " text for key " + key);
        }
        return text;
    }
}
