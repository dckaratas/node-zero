package com.nodezero.server.web;

import com.nodezero.server.protocol.CounterCatalog;
import com.nodezero.server.session.SessionHost;
import com.nodezero.sim.content.Language;
import java.util.Locale;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/**
 * Counter catalog for one session (slice 5b, decisions V/W). The client fetches it on connect and
 * again when the snapshot's insights change. Private caching with an ETag: the response depends on
 * the session's insights, so an unchanged catalog costs a 304 instead of the full body.
 */
@RestController
class CatalogController {

    private final SessionHost host;
    private final CounterCatalog catalog;

    CatalogController(SessionHost host, CounterCatalog catalog) {
        this.host = host;
        this.catalog = catalog;
    }

    @GetMapping("/api/sessions/{id}/catalog")
    ResponseEntity<CounterCatalog.Catalog> catalog(
            @PathVariable String id, @RequestParam(defaultValue = "en") String lang, WebRequest request) {
        Language language;
        try {
            language = Language.valueOf(lang.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return ResponseEntity.badRequest().build();
        }
        return host.find(id)
                .map(session -> catalog.visible(language, session.state().progress().insights()))
                .map(body -> {
                    String etag = "\"" + Integer.toHexString(body.hashCode()) + "\"";
                    if (request.checkNotModified(etag)) {
                        return ResponseEntity.status(304).eTag(etag).<CounterCatalog.Catalog>build();
                    }
                    return ResponseEntity.ok().eTag(etag).cacheControl(CacheControl.noCache().cachePrivate()).body(body);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
