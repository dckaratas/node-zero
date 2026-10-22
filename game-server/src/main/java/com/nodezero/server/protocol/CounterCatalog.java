package com.nodezero.server.protocol;

import com.nodezero.sim.content.ActionDef;
import com.nodezero.sim.content.Content;
import com.nodezero.sim.content.CounterDef;
import com.nodezero.sim.content.Language;
import com.nodezero.sim.engine.Rules;
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
            int toil,
            double applyTimeS,
            List<String> requiresCounters,
            String requiresInsight,
            List<String> grantsInsights,
            String tradeoff,
            List<Option> options) {}

    /** @param applyTimeS overrides the counter's build time, or {@code null} */
    public record Option(String id, String name, Double applyTimeS, String tradeoff) {}

    /** An incident action card (slice 7b): costs crew, not credits, and cools down. */
    public record Action(String id, String name, String realTech, String detail, int toil, double cooldownS,
            List<ActionOption> options) {}

    public record ActionOption(String id, String name, String detail) {}

    /**
     * Response body of {@code GET /api/sessions/{id}/catalog}. Actions are never gated: on-call can always
     * restart or roll back, knowing when it helps is the lesson.
     */
    public record Catalog(String language, int crewPerShift, List<Item> counters, List<Action> actions) {}

    private final Map<Language, List<Item>> byLanguage = new EnumMap<>(Language.class);
    private final Map<Language, List<Action>> actionsByLanguage = new EnumMap<>(Language.class);
    private final int crewPerShift;

    public CounterCatalog(Rules rules) {
        Content content = rules.content();
        this.crewPerShift = rules.balance().crewPerShift();
        for (Language language : Language.values()) {
            List<Item> items = new ArrayList<>();
            for (CounterDef def : content.counters().values()) {
                items.add(item(def, language, content));
            }
            byLanguage.put(language, List.copyOf(items));
            List<Action> actions = new ArrayList<>();
            for (ActionDef def : content.actions().values()) {
                actions.add(action(def, language));
            }
            actionsByLanguage.put(language, List.copyOf(actions));
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
        return new Catalog(language.name().toLowerCase(Locale.ROOT), crewPerShift, List.copyOf(visible),
                actionsByLanguage.get(language));
    }

    private static Action action(ActionDef def, Language language) {
        List<ActionOption> options = new ArrayList<>();
        for (ActionDef.ActionOption o : def.options()) {
            options.add(new ActionOption(o.id(), o.name().in(language), o.detail().in(language)));
        }
        return new Action(def.id(), def.name().in(language), def.realTech(), def.detail().in(language), def.toil(),
                def.cooldownS(), List.copyOf(options));
    }

    private static Item item(CounterDef def, Language language, Content content) {
        List<Option> options = new ArrayList<>();
        for (CounterDef.CounterOption o : def.options()) {
            options.add(new Option(o.id(), o.name().in(language), o.applyTimeS(),
                    content.messages().text(language, o.tradeoffKey())));
        }
        return new Item(def.id(), def.name().in(language), def.realTech(), def.cost().credits(),
                def.cost().upkeepPerWave(), def.toil(), def.applyTimeS(), def.requiresCounters(), def.requiresInsight(),
                def.grantsInsights(), content.messages().text(language, def.tradeoffKey()), List.copyOf(options));
    }
}
