package com.nodezero.server.web;

import com.nodezero.server.protocol.CodexBook;
import com.nodezero.server.session.SessionHost;
import com.nodezero.sim.content.Language;
import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/**
 * The codex entries a session has opened (slice 6b, decision AB). The client fetches it again when
 * the snapshot's codex list changes; an unchanged body is a 304 thanks to the ETag.
 */
@RestController
class CodexController {

    private final SessionHost host;
    private final CodexBook book;

    CodexController(SessionHost host, CodexBook book) {
        this.host = host;
        this.book = book;
    }

    @GetMapping("/api/sessions/{id}/codex")
    ResponseEntity<CodexBook.Codex> codex(
            @PathVariable String id, @RequestParam(defaultValue = "en") String lang, WebRequest request) {
        Optional<Language> language = Languages.parse(lang);
        if (language.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return host.find(id)
                .map(session -> book.opened(language.get(), session.state().progress().codex()))
                .map(body -> {
                    String etag = "\"" + Integer.toHexString(body.hashCode()) + "\"";
                    if (request.checkNotModified(etag)) {
                        return ResponseEntity.status(304).eTag(etag).<CodexBook.Codex>build();
                    }
                    return ResponseEntity.ok().eTag(etag).cacheControl(CacheControl.noCache().cachePrivate()).body(body);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
