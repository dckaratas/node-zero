package com.nodezero.sim.content;

/**
 * A codex article (ADR-0008): the real-world concept behind a threat or counter, in both content
 * languages. The body is plain text; a blank line separates paragraphs.
 */
public record CodexEntry(String id, LocalizedText title, LocalizedText body) {

    public CodexEntry {
        Require.id(id, "id");
        Require.present(title, "title");
        Require.present(body, "body");
    }
}
