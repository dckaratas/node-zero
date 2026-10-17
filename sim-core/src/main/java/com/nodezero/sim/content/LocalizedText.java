package com.nodezero.sim.content;

/** Inline text in both content languages (ADR-0009). */
public record LocalizedText(String en, String tr) {

    public LocalizedText {
        Require.nonBlank(en, "en");
        Require.nonBlank(tr, "tr");
    }

    public String in(Language language) {
        return switch (language) {
            case EN -> en;
            case TR -> tr;
        };
    }
}
