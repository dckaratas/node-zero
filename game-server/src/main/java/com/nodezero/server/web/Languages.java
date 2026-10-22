package com.nodezero.server.web;

import com.nodezero.sim.content.Language;
import java.util.Locale;
import java.util.Optional;

/** Content language from a request parameter such as {@code en} or {@code tr}. */
final class Languages {

    private Languages() {}

    static Optional<Language> parse(String code) {
        try {
            return Optional.of(Language.valueOf(code.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }
}
