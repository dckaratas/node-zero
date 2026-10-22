package com.nodezero.sim.content;

import com.nodezero.sim.state.ResourceId;
import java.util.List;

/**
 * An incident action from {@code content/actions/*.yaml} (slice 7b, ADR-0018 decisions AF/AH): something
 * the on-call crew does by hand during an incident. It costs no credits but crew (toil) and has a
 * cooldown; every action also hurts in some way, so using one at the wrong time burns the shield.
 *
 * @param effects effects of every option; an option adds its own
 * @param codex entries opened the first time the action is run
 */
public record ActionDef(
        String id,
        LocalizedText name,
        String realTech,
        LocalizedText detail,
        int toil,
        double cooldownS,
        List<ActionEffect> effects,
        List<ActionOption> options,
        List<String> codex) {

    public ActionDef {
        Require.id(id, "id");
        Require.present(name, "name");
        Require.nonBlank(realTech, "real_tech");
        Require.present(detail, "detail");
        Require.atLeast(toil, 1, "toil");
        Require.nonNegative(cooldownS, "cooldown_s");
        effects = Require.orEmpty(effects);
        options = Require.orEmpty(options);
        codex = Require.orEmpty(codex);
        codex.forEach(c -> Require.id(c, "codex[]"));
    }

    /** One way to carry out the action, e.g. {@code truncate} vs {@code rm} for freeing disk. */
    public record ActionOption(String id, LocalizedText name, LocalizedText detail, List<ActionEffect> effects) {
        public ActionOption {
            Require.id(id, "options[].id");
            Require.present(name, "options[].name");
            Require.present(detail, "options[].detail");
            effects = Require.orEmpty(effects);
        }
    }

    /**
     * Declarative effect. Without {@code fromS}/{@code toS} it happens once, when the action runs;
     * with them it holds between those seconds after the start (e.g. downtime, a cold JVM).
     */
    public record ActionEffect(String type, ResourceId target, Double value, Double fromS, Double toS) {
        public ActionEffect {
            Require.snake(type, "type");
            if ((fromS == null) != (toS == null)) {
                throw new IllegalArgumentException("effect " + type + " needs both from_s and to_s, or neither");
            }
            if (fromS != null && (fromS < 0 || toS <= fromS)) {
                throw new IllegalArgumentException("effect " + type + " needs 0 <= from_s < to_s");
            }
        }

        public boolean instant() {
            return fromS == null;
        }
    }
}
