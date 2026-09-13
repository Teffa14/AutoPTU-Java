package io.autoptu.core.hook;

import java.util.Objects;

/** Declarative triggered-action candidate produced by an action-window hook. */
public record ActionWindowCandidate(String reactingCombatantId, String actionKey, String triggerKey) {
    public ActionWindowCandidate {
        if (reactingCombatantId == null || reactingCombatantId.isBlank()) {
            throw new IllegalArgumentException("reacting combatant id is required");
        }
        reactingCombatantId = reactingCombatantId.strip();
        if (actionKey == null || actionKey.isBlank()) {
            throw new IllegalArgumentException("action key is required");
        }
        actionKey = actionKey.strip();
        if (triggerKey == null || triggerKey.isBlank()) {
            throw new IllegalArgumentException("trigger key is required");
        }
        triggerKey = triggerKey.strip();
    }
}
