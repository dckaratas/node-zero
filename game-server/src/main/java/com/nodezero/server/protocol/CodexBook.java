package com.nodezero.server.protocol;

import com.nodezero.sim.content.CodexEntry;
import com.nodezero.sim.content.Content;
import com.nodezero.sim.content.Language;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Codex articles in one language (slice 6b, decision AB). Only entries the session has opened are
 * served: an unopened article could name the root cause a postmortem is about to ask for. Built once
 * at startup; immutable and thread-safe.
 */
public final class CodexBook {

    /** One article; {@code body} is plain text with blank lines between paragraphs. */
    public record Article(String id, String title, String body) {}

    /** Response body of {@code GET /api/sessions/{id}/codex}, in the order the player opened them. */
    public record Codex(String language, List<Article> entries) {}

    private final Map<Language, Map<String, Article>> byLanguage = new EnumMap<>(Language.class);

    public CodexBook(Content content) {
        for (Language language : Language.values()) {
            Map<String, Article> articles = new LinkedHashMap<>();
            for (CodexEntry e : content.codex().values()) {
                articles.put(e.id(), new Article(e.id(), e.title().in(language), e.body().in(language)));
            }
            byLanguage.put(language, articles);
        }
    }

    public Codex opened(Language language, Collection<String> ids) {
        Map<String, Article> articles = byLanguage.get(language);
        List<Article> entries = new ArrayList<>();
        for (String id : ids) {
            Article article = articles.get(id);
            if (article != null) {
                entries.add(article);
            }
        }
        return new Codex(language.name().toLowerCase(Locale.ROOT), List.copyOf(entries));
    }
}
