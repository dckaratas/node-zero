package com.nodezero.sim.content;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and validates content (ADR-0005). Two layers of checks:
 * <ol>
 *   <li>binding: unknown fields, wrong types and record-constructor rules, reported with file and line;
 *   <li>cross-file: ids match file names, references resolve, every text key exists in EN and TR.
 * </ol>
 * Cross-file problems are collected and reported together so authors can fix them in one pass.
 */
public final class ContentLoader {

    public static final String DEFAULT_ROOT = "content";

    private static final YAMLMapper MAPPER = YAMLMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            // Matches "disk" to DISK with locale-free comparison (safe under tr_TR).
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            // Missing primitives arrive as 0/false; record constructors reject the invalid ones (e.g. chapter >= 1).
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .build();

    private static final TypeReference<LinkedHashMap<String, String>> TEXTS = new TypeReference<>() {};

    private final ContentSource source;

    private ContentLoader(ContentSource source) {
        this.source = source;
    }

    public static Content loadDefault() {
        return load(ContentSource.classpath(DEFAULT_ROOT));
    }

    public static Content load(ContentSource source) {
        return new ContentLoader(source).load();
    }

    private Content load() {
        Index index = read("index.yaml", Index.class);
        List<String> problems = new ArrayList<>();

        Map<String, ThreatDef> threats = new LinkedHashMap<>();
        for (String file : index.threats()) {
            ThreatDef threat = read(file, ThreatFile.class).threat();
            String expectedId = baseName(file);
            if (!threat.id().equals(expectedId)) {
                problems.add(file + ": id '" + threat.id() + "' must match the file name '" + expectedId + "'");
            }
            if (threats.putIfAbsent(threat.id(), threat) != null) {
                problems.add(file + ": duplicate threat id '" + threat.id() + "'");
            }
        }

        Map<String, CounterDef> counters = new LinkedHashMap<>();
        for (String file : index.counters()) {
            for (CounterDef counter : read(file, CounterFile.class).counters()) {
                if (counters.putIfAbsent(counter.id(), counter) != null) {
                    problems.add(file + ": duplicate counter id '" + counter.id() + "'");
                }
            }
        }

        Map<String, ActionDef> actions = new LinkedHashMap<>();
        for (String file : index.actions()) {
            for (ActionDef action : read(file, ActionFile.class).actions()) {
                if (actions.putIfAbsent(action.id(), action) != null) {
                    problems.add(file + ": duplicate action id '" + action.id() + "'");
                }
            }
        }

        Map<String, CodexEntry> codex = new LinkedHashMap<>();
        for (String file : index.codex()) {
            for (CodexEntry entry : read(file, CodexFile.class).codex()) {
                if (codex.putIfAbsent(entry.id(), entry) != null) {
                    problems.add(file + ": duplicate codex id '" + entry.id() + "'");
                }
            }
        }

        Messages messages = new Messages(read(index.messages().en(), TEXTS), read(index.messages().tr(), TEXTS));

        checkReferences(threats, counters, problems);
        checkCodex(threats, counters, actions, codex, problems);
        checkTexts(threats, counters, messages, problems);
        if (!problems.isEmpty()) {
            throw new ContentException("invalid content:\n  - " + String.join("\n  - ", problems));
        }
        return new Content(threats, counters, actions, codex, messages);
    }

    private static void checkReferences(
            Map<String, ThreatDef> threats, Map<String, CounterDef> counters, List<String> problems) {
        threats.values().forEach(t -> t.counters().stream()
                .filter(id -> !counters.containsKey(id))
                .forEach(id -> problems.add("threat " + t.id() + ": unknown counter '" + id + "'")));
        counters.values().stream()
                .filter(c -> c.betterAlternative() != null && !counters.containsKey(c.betterAlternative()))
                .forEach(c -> problems.add(
                        "counter " + c.id() + ": unknown better_alternative '" + c.betterAlternative() + "'"));
        counters.values().forEach(c -> c.requiresCounters().stream()
                .filter(id -> !counters.containsKey(id))
                .forEach(id -> problems.add("counter " + c.id() + ": unknown requires_counters '" + id + "'")));
        // Every insight a counter requires must be obtainable from some other counter.
        counters.values().stream()
                .filter(c -> c.requiresInsight() != null)
                .filter(c -> counters.values().stream().noneMatch(g -> g.grantsInsights().contains(c.requiresInsight())))
                .forEach(c -> problems.add(
                        "counter " + c.id() + ": no counter grants insight '" + c.requiresInsight() + "'"));
    }

    /** Every codex link resolves, and every entry is linked from somewhere (no dead articles). */
    private static void checkCodex(Map<String, ThreatDef> threats, Map<String, CounterDef> counters,
            Map<String, ActionDef> actions, Map<String, CodexEntry> codex, List<String> problems) {
        Map<String, String> linkedBy = new LinkedHashMap<>();
        threats.values().forEach(t -> t.codex().forEach(id -> linkedBy.putIfAbsent(id, "threat " + t.id())));
        counters.values().forEach(c -> c.codex().forEach(id -> linkedBy.putIfAbsent(id, "counter " + c.id())));
        actions.values().forEach(a -> a.codex().forEach(id -> linkedBy.putIfAbsent(id, "action " + a.id())));
        linkedBy.forEach((id, owner) -> {
            if (!codex.containsKey(id)) {
                problems.add(owner + ": unknown codex entry '" + id + "'");
            }
        });
        codex.keySet().stream()
                .filter(id -> !linkedBy.containsKey(id))
                .forEach(id -> problems.add("codex " + id + ": not referenced by any threat, counter or action"));
    }

    private static void checkTexts(
            Map<String, ThreatDef> threats, Map<String, CounterDef> counters, Messages messages, List<String> problems) {
        Map<String, String> owners = new LinkedHashMap<>();
        threats.values().forEach(t -> t.textKeys().forEach(k -> owners.putIfAbsent(k, "threat " + t.id())));
        counters.values().forEach(c -> c.textKeys().forEach(k -> owners.putIfAbsent(k, "counter " + c.id())));
        owners.putIfAbsent(ThreatDef.QUIET_BRIEFING_KEY, "the briefing of quiet waves");

        for (Language language : Language.values()) {
            Map<String, String> texts = messages.in(language);
            owners.forEach((key, owner) -> {
                if (!texts.containsKey(key)) {
                    problems.add(owner + ": missing " + language + " text for '" + key + "'");
                }
            });
            texts.keySet().stream()
                    .filter(key -> !owners.containsKey(key))
                    .forEach(key -> problems.add(language + " texts: unused key '" + key + "'"));
        }
    }

    private <T> T read(String file, Class<T> type) {
        try (InputStream in = open(file)) {
            return MAPPER.readValue(in, type);
        } catch (JsonProcessingException e) {
            throw ContentException.from(file, e);
        } catch (IOException e) {
            throw new ContentException(file + ": " + e.getMessage(), e);
        }
    }

    private <T> T read(String file, TypeReference<T> type) {
        try (InputStream in = open(file)) {
            return MAPPER.readValue(in, type);
        } catch (JsonProcessingException e) {
            throw ContentException.from(file, e);
        } catch (IOException e) {
            throw new ContentException(file + ": " + e.getMessage(), e);
        }
    }

    private InputStream open(String file) throws IOException {
        InputStream in = source.open(file);
        if (in == null) {
            throw new ContentException(file + ": file not found");
        }
        return in;
    }

    private static String baseName(String file) {
        String name = file.substring(file.lastIndexOf('/') + 1);
        return name.endsWith(".yaml") ? name.substring(0, name.length() - ".yaml".length()) : name;
    }

    /** {@code content/index.yaml}: which files to load, in which order. */
    record Index(List<String> threats, List<String> counters, List<String> actions, List<String> codex,
            MessageFiles messages) {
        Index {
            threats = Require.orEmpty(threats);
            counters = Require.orEmpty(counters);
            actions = Require.orEmpty(actions);
            codex = Require.orEmpty(codex);
            Require.present(messages, "messages");
        }
    }

    record MessageFiles(String en, String tr) {
        MessageFiles {
            Require.nonBlank(en, "messages.en");
            Require.nonBlank(tr, "messages.tr");
        }
    }

    record ThreatFile(ThreatDef threat) {
        ThreatFile {
            Require.present(threat, "threat");
        }
    }

    record CounterFile(List<CounterDef> counters) {
        CounterFile {
            counters = Require.nonEmpty(counters, "counters");
        }
    }

    record ActionFile(List<ActionDef> actions) {
        ActionFile {
            actions = Require.nonEmpty(actions, "actions");
        }
    }

    record CodexFile(List<CodexEntry> codex) {
        CodexFile {
            codex = Require.nonEmpty(codex, "codex");
        }
    }
}
