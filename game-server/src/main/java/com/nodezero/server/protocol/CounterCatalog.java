package com.nodezero.server.protocol;

import com.nodezero.sim.content.Content;
import com.nodezero.sim.content.CounterDef;
import com.nodezero.sim.content.Language;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The counters a player can buy, with texts resolved in one language (slice 5b, decision V).
 * Counters that need an insight stay hidden until the player has it (decision W): finding the cause is
 * part of the game, so "Create index" must not show up before pg_stat_statements does. The filter runs
 * on the server, like the rest of the fog of war. Built once at startup; immutable and thread-safe.
 */
public final class CounterCatalog {

    /** What the client shows on a counter card. */
    public record Item(
            String id,
            String name,
            String realTech,
            int credits,
            int upkeepPerWave,
            double applyTimeS,
            List<String> requiresCounters,
            String requiresInsight,
            List<String> grantsInsights,
            String tradeoff,
            List<Option> options) {}

    /** @param applyTimeS overrides the counter's build time, or {@code null} */
    public record Option(String id, String name, Double applyTimeS, String tradeoff) {}

    /** Response body of {@code GET /api/sessions/{id}/catalog}. */
    public record Catalog(String language, List<Item> counters) {}

    private final Map<Language, List<Item>> byLanguage = new EnumMap<>(Language.class);

    public CounterCatalog(Content content) {
        for (Language language : Language.values()) {
            List<Item> items = new ArrayList<>();
            for (CounterDef def : content.counters().values()) {
                items.add(item(def, language, content));
            }
            byLanguage.put(language, List.copyOf(items));
        }
    }

    /** Every counter the player may see given the insights they have, in content order. */
    public Catalog visible(Language language, Collection<String> insights) {
        List<Item> visible = new ArrayList<>();
        for (Item item : byLanguage.get(language)) {
            if (item.requiresInsight() == null || insights.contains(item.requiresInsight())) {
                visible.add(item);
            }
        }
        return new Catalog(language.name().toLowerCase(Locale.ROOT), List.copyOf(visible));
    }

    private static Item item(CounterDef def, Language language, Content content) {
        List<Option> options = new ArrayList<>();
        for (CounterDef.CounterOption o : def.options()) {
            options.add(new Option(o.id(), o.name().in(language), o.applyTimeS(),
                    content.messages().text(language, o.tradeoffKey())));
        }
        return new Item(def.id(), def.name().in(language), def.realTech(), def.cost().credits(),
                def.cost().upkeepPerWave(), def.applyTimeS(), def.requiresCounters(), def.requiresInsight(),
                def.grantsInsights(), content.messages().text(language, def.tradeoffKey()), List.copyOf(options));
    }
}
