package io.autoptu.core.hook;

import java.util.List;

/**
 * Server-authoritative decision contract for Trainer Features that may react to a battle trigger
 * by switching an active combatant.
 *
 * <p>The plan describes eligibility-time policy only. A renderer may display the decision, but it
 * must not recalculate AP, replacement legality, switch policy, or trigger guards.</p>
 */
public record SwitchTriggerDecisionPlan(
        String featureName,
        Trigger trigger,
        String actorId,
        List<String> replacementIds,
        String defaultReplacementId,
        int requiredAp,
        int consumeAp,
        String phase,
        boolean optional,
        SwitchPolicy switchPolicy,
        String sentOutEffectKey,
        String dedupeEffectKey
) {
    public SwitchTriggerDecisionPlan {
        featureName = require(featureName, "featureName");
        if (trigger == null) throw new IllegalArgumentException("trigger is required");
        actorId = require(actorId, "actorId");
        replacementIds = replacementIds == null ? List.of() : replacementIds.stream()
                .map(value -> require(value, "replacementId"))
                .distinct()
                .toList();
        if (replacementIds.isEmpty()) throw new IllegalArgumentException("replacementIds are required");
        defaultReplacementId = require(defaultReplacementId, "defaultReplacementId");
        if (!replacementIds.contains(defaultReplacementId)) {
            throw new IllegalArgumentException("defaultReplacementId must be a replacement candidate");
        }
        if (requiredAp < 0) throw new IllegalArgumentException("requiredAp cannot be negative");
        if (consumeAp < 0) throw new IllegalArgumentException("consumeAp cannot be negative");
        phase = require(phase, "phase");
        if (switchPolicy == null) throw new IllegalArgumentException("switchPolicy is required");
        sentOutEffectKey = normalize(sentOutEffectKey);
        dedupeEffectKey = normalize(dedupeEffectKey);
    }

    public enum Trigger {
        OPPONENT_SEND_OUT("opponent_send_out"),
        ALLY_FAINT("ally_faint");

        private final String oracleName;

        Trigger(String oracleName) {
            this.oracleName = oracleName;
        }

        public String oracleName() {
            return oracleName;
        }
    }

    public record SwitchPolicy(
            boolean applyTagIn,
            boolean allowReplacementTurn,
            boolean allowImmediate,
            boolean allowRecursiveTriggers
    ) {
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.strip();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
