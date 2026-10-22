package com.nodezero.server.web;

import com.nodezero.sim.content.Language;
import com.nodezero.sim.engine.Game;
import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/**
 * Content texts the client resolves by key (ADR-0009, slice 6 decision AC): postmortem questions,
 * hints, station comms. Only prefixes that give nothing away are served; counter tradeoffs come
 * resolved in the catalog, which hides insight-gated counters (decision W). Static per server build,
 * so it is cached publicly and revalidated by ETag.
 */
@RestController
class I18nController {

    private static final String[] PUBLIC_PREFIXES = {"pm.", "hint.", "comms."};

    private final Map<Language, Map<String, String>> bundles = new EnumMap<>(Language.class);
    private final Map<Language, String> etags = new EnumMap<>(Language.class);

    I18nController(Game game) {
        for (Language language : Language.values()) {
            Map<String, String> bundle = new LinkedHashMap<>();
            game.rules().content().messages().in(language).forEach((key, text) -> {
                for (String prefix : PUBLIC_PREFIXES) {
                    if (key.startsWith(prefix)) {
                        bundle.put(key, text);
                    }
                }
            });
            bundles.put(language, Collections.unmodifiableMap(bundle));
            etags.put(language, "\"" + Integer.toHexString(bundle.hashCode()) + "\"");
        }
    }

    @GetMapping("/api/i18n/{lang}")
    ResponseEntity<Map<String, String>> bundle(@PathVariable String lang, WebRequest request) {
        Language language;
        try {
            language = Language.valueOf(lang.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return ResponseEntity.notFound().build();
        }
        String etag = etags.get(language);
        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(304).eTag(etag).build();
        }
        return ResponseEntity.ok().eTag(etag).cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(bundles.get(language));
    }
}
