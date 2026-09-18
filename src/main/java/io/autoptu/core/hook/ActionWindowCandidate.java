package io.autoptu.core.hook;

/** Declarative triggered-action candidate produced by an action-window hook. */
public record ActionWindowCandidate(
        String reactingCombatantId,
        String actionKey,
        String triggerKey,
        String triggeringCombatantId
) {
    public ActionWindowCandidate {
        reactingCombatantId = requireText(reactingCombatantId, "reacting combatant id");
        actionKey = requireText(actionKey, "action key");
        triggerKey = requireText(triggerKey, "trigger key");
        triggeringCombatantId = normalizeOptionalText(triggeringCombatantId);
    }

    /** Compatibility constructor for action windows that do not yet expose a source combatant. */
    public ActionWindowCandidate(String reactingCombatantId, String actionKey, String triggerKey) {
        this(reactingCombatantId, actionKey, triggerKey, "");
    }

    public boolean hasTriggeringCombatant() {
        return !triggeringCombatantId.isEmpty();
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.strip();
    }

    private static String normalizeOptionalText(String value) {
        return value == null ? "" : value.strip();
    }
}
